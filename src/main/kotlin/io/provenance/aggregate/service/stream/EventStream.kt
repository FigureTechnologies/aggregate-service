package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.*
import com.tinder.scarlet.Message
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.service.Config
import io.provenance.aggregate.service.DefaultDispatcherProvider
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.models.*
import io.provenance.aggregate.service.stream.models.extensions.blockEvents
import io.provenance.aggregate.service.stream.models.extensions.isEmpty
import io.provenance.aggregate.service.stream.models.extensions.txEvents
import io.provenance.aggregate.service.stream.models.extensions.txHash
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.lang.IllegalStateException
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@OptIn(FlowPreview::class)
interface IEventStream {
    suspend fun streamBlocks(): Flow<Either<Throwable, StreamBlock>>
}

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintService: TendermintService,
    private val dynamo: AwsDynamoInterface,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val options: Options = Options.DEFAULT
) : IEventStream {

    companion object {
        const val DEFAULT_BATCH_SIZE = 4
        const val TENDERMINT_MAX_QUERY_RANGE = 20
        const val DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS = 100
    }

    data class Options(
        val concurrency: Int,
        val batchSize: Int,
        val fromHeight: Long?,
        val toHeight: Long?,
        val skipIfEmpty: Boolean,
        val skipIfSeen: Boolean
    ) {
        companion object {
            val DEFAULT: Options = Builder().build()
            fun builder() = Builder()
        }

        class Builder {
            var concurrency: Int = DEFAULT_CONCURRENCY
            var batchSize: Int = DEFAULT_BATCH_SIZE
            var fromHeight: Long? = null
            var toHeight: Long? = null
            var skipIfEmpty: Boolean = true
            var skipIfSeen: Boolean = false

            fun concurrency(value: Int) = apply { concurrency = value }
            fun batchSize(value: Int) = apply { batchSize = value }
            fun fromHeight(value: Long?) = apply { fromHeight = value }
            fun fromHeight(value: Long) = apply { fromHeight = value }
            fun toHeight(value: Long) = apply { toHeight = value }
            fun skipIfEmpty(value: Boolean) = apply { skipIfEmpty = value }
            fun skipIfSeen(value: Boolean) = apply { skipIfSeen = value }

            fun build(): Options = Options(
                concurrency = concurrency,
                batchSize = batchSize,
                fromHeight = fromHeight,
                toHeight = toHeight,
                skipIfEmpty = skipIfEmpty,
                skipIfSeen = skipIfSeen
            )
        }
    }

    class Factory(
        private val config: Config,
        private val moshi: Moshi,
        private val eventStreamBuilder: Scarlet.Builder,
        private val tendermintService: TendermintService,
        private val dynamoClient: AwsDynamoInterface,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    ) {
        private fun noop(_options: EventStream.Options.Builder) {}

        fun create(setOptions: (options: EventStream.Options.Builder) -> Unit = ::noop): EventStream {
            val optionsBuilder = Options.Builder()
                .batchSize(config.event.stream.batchSize)
                .skipIfEmpty(true)
            setOptions(optionsBuilder)
            return create(optionsBuilder.build())
        }

        fun create(options: EventStream.Options): EventStream {
            val lifecycle = LifecycleRegistry(config.event.stream.throttleDurationMs)
            val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
            val tendermintRpc: TendermintRPCStream = scarlet.create()
            val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)

            return EventStream(
                eventStreamService,
                tendermintService,
                dynamoClient,
                moshi,
                options = options,
                dispatchers = dispatchers
            )
        }
    }

    sealed class MessageType {
        object Empty : MessageType()
        data class NewBlock(val block: NewBlockResult) : MessageType()
    }

    private val log = logger()

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    val rpcEmptyResponseReader: JsonAdapter<RpcResponse<JSONObject>> = moshi.adapter(
        Types.newParameterizedType(
            RpcResponse::class.java,
            JSONObject::class.java
        )
    )

    val rpcNewBlockResponseReader: JsonAdapter<RpcResponse<NewBlockResult>> = moshi.adapter(
        Types.newParameterizedType(
            RpcResponse::class.java,
            NewBlockResult::class.java
        )
    )

    /**
     * Serialize data of a given class type
     */
    fun <T> serialize(clazz: Class<T>, data: T) = moshi.adapter<T>(clazz).toJson(data)

    private fun deserializeMessage(input: String): MessageType? {
        try {
            if (rpcEmptyResponseReader.fromJson(input)?.isEmpty() ?: true) {
                return MessageType.Empty
            }
        } catch (_: JsonDataException) {
        }
        return rpcNewBlockResponseReader.fromJson(input)
            ?.let {
                it.result?.let { MessageType.NewBlock(it) }
            }
    }

    private fun getBlockHeightQueryRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
        if (minHeight > maxHeight) {
            return emptySequence()
        }
        val step = TENDERMINT_MAX_QUERY_RANGE
        return sequence {
            var i = minHeight
            var j = i + step - 1
            while (j <= maxHeight) {
                yield(Pair(i, j))
                i = j + 1
                j = i + step - 1
            }
            // If there's a gap between the last range and `maxHeight`, yield one last pair to fill it:
            if (i <= maxHeight) {
                yield(Pair(i, maxHeight))
            }
        }
    }

    private suspend fun fetchBlockHeightsInRange(minHeight: Long, maxHeight: Long): List<Long> {
        if (minHeight > maxHeight) {
            return emptyList()
        }

        // invariant
        assert((maxHeight - minHeight) <= TENDERMINT_MAX_QUERY_RANGE) {
            "Difference between (minHeight, maxHeight) can be at maximum $TENDERMINT_MAX_QUERY_RANGE"
        }

        return (tendermintService.blockchain(minHeight, maxHeight)
            .result
            ?.blockMetas
            .let {
                if (options.skipIfEmpty) {
                    it?.filter { it.numTxs ?: 0 > 0 }
                } else {
                    it
                }
            }?.mapNotNull { it.header?.height }
            ?: emptyList<Long>())
            .sortedWith(naturalOrder<Long>())
    }

    /**
     * Query a block by height, returning any events associated with the block.
     *
     *  @param heightOrBlock Fetch a block, plus its events, by its height or the `Block` model itself.
     *  @param skipIfNoTxs If `skipIfNoTxs` is true, if the block at the given height has no transactions, null will
     *  be returned in its place.
     */
    private suspend fun queryBlock(
        heightOrBlock: Either<Long, Block>,
        skipIfNoTxs: Boolean = true
    ): StreamBlock? {
        val block: Block? = when (heightOrBlock) {
            is Either.Left<Long> -> tendermintService.block(heightOrBlock.value).result?.block
            is Either.Right<Block> -> heightOrBlock.value
        }

        if (skipIfNoTxs && block?.data?.txs?.size ?: 0 == 0) {
            return null
        }


        return block?.run {
            val blockResponse = tendermintService.blockResults(block.header?.height).result
            val blockEvents: List<BlockEvent> = blockResponse.blockEvents()
            val txEvents: List<TxEvent> = blockResponse.txEvents { index: Int -> block.txHash(index) ?: "" }
            return StreamBlock(this, blockEvents, txEvents)
        }
    }

    /***
     * Query a collections of blocks by their heights.
     *
     * Note: it is assumed the specified blocks already exists. No check will be performed to verify existence!
     *
     * @param blockHeights The heights of the blocks to query
     * @return A Flow of found historical blocks along with events associated with each block, if any.
     */
    private suspend fun queryBlocks(blockHeights: Iterable<Pair<Long, BlockStorageMetadata?>>): Flow<StreamBlock> {
        // Chunk up the heights of returned blocks, then for the heights in each block,
        // concurrently fetch the events for each block at the given height:
        return blockHeights.chunked(options.batchSize)
            .asFlow()
            .transform { chunkOfHeights: List<Pair<Long, BlockStorageMetadata?>> ->
                val blocks: List<StreamBlock> = coroutineScope {
                    chunkOfHeights.map { (height: Long, metadata: BlockStorageMetadata?) ->
                        async {
                            //log.info("streamHistoricalBlocks::queryBlockRange::async<${Thread.currentThread().id}>")
                            queryBlock(Either.Left(height), skipIfNoTxs = options.skipIfEmpty)
                                ?.let {
                                    it.copy(metadata = metadata)
                                }
                        }
                    }
                        .awaitAll()
                        .filterNotNull()
                }
                for (block in blocks) {
                    emit(block)
                }
            }
            .flowOn(dispatchers.io())
    }

    @JvmName("queryBlocksNoMetadata")
    private suspend fun queryBlocks(blockHeights: Iterable<Long>): Flow<StreamBlock> =
        queryBlocks(blockHeights.map { Pair(it, null) })

    /**
     * Constructs a Flow of historical blocks and associated events based on a starting height. Blocks will be streamed
     * from the given starting height up to the latest block height, as determined by the start of the Flow.
     *
     * @param fromHeight Stream blocks from the given starting height.
     * @param concurrency The concurrency limit for the Flow's `flatMapMerge` operation. See
     * https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.Flow/flat-map-merge.html
     * @return A Flow of historical blocks and associated events
     */
    suspend fun streamHistoricalBlocks(): Flow<Either<Throwable, StreamBlock>> {
        val startingHeight: Long = options.fromHeight ?: throw IllegalStateException("No starting height set")
        val lastBlockHeight: Long = tendermintService.abciInfo().result?.response?.lastBlockHeight ?: 0

        log.info("streamHistoricalBlocks::streaming blocks: $startingHeight to $lastBlockHeight")
        log.info("streamHistoricalBlocks::batch size = ${options.batchSize}")

        // Since each pair will be TENDERMINT_MAX_QUERY_RANGE apart, and we want the cumulative number of heights
        // to query to be DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS, we need
        // floor(max(TENDERMINT_MAX_QUERY_RANGE, DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS) / min(TENDERMINT_MAX_QUERY_RANGE, DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS))
        // chunks:
        val xValue = TENDERMINT_MAX_QUERY_RANGE.toDouble()
        val yValue = DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS.toDouble()
        val numChunks: Int = floor(max(xValue, yValue) / min(xValue, yValue)).toInt()

        return getBlockHeightQueryRanges(startingHeight, lastBlockHeight)
            .chunked(numChunks)
            .asFlow()
            .map { heightPairChunk: List<Pair<Long, Long>> -> // each pair will be `TENDERMINT_MAX_QUERY_RANGE` units apart
                // How tracking blocks works:
                //   1. For a given list of height pairs <minHeight, maxHeight>, compute the overall <spanMinHeight, spanMaxHeight>
                //   2. Generate a new sequence [spanMinHeight..spanMaxHeight]
                val spanMinHeight: Long = heightPairChunk.minOf { it.first }
                val spanMaxHeight: Long = heightPairChunk.maxOf { it.second }
                val fullBlockHeights: Set<Long> = (spanMinHeight..spanMaxHeight).toSet()

                // invariant:
                assert(fullBlockHeights.size <= DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS)

                // There are two ways to handle blocks that have been seen already and recorded in Dynamo, but are
                // present in the stream:
                //
                // 1. Filter them out completely so that they don't appear in the resulting Flow<StreamBlock> at all
                //
                // 2. Include the given StreamBlock in the resulting Flow, but mark the StreamBlock's `metadata`
                //    property as null/non-null based on the presence of the block in upstream.
                val (seenBlockMap: Map<Long, BlockStorageMetadata>, availableBlocks: List<Long>) = coroutineScope {

                    val seenBlockMap: Map<Long, BlockStorageMetadata> =
                        dynamo.getBlockMetadataMap(fullBlockHeights)  // Capped at size=DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS

                    val availableBlocks = heightPairChunk
                        // Filter out spans in which all block heights have been seen already:
                        .filter { (minHeight, maxHeight) -> !((minHeight..maxHeight).all { it in seenBlockMap }) }
                        // Fetch the blocks in range chunk [minHeight, maxHeight]:
                        .map { (minHeight, maxHeight) ->
                            async { fetchBlockHeightsInRange(minHeight, maxHeight) }
                        }.awaitAll()
                        // Flatten all the existing block height lists:
                        .flatten()
                        // Remove any that have already been recorded in Dynamo:
                        .let { blocks ->
                            if (options.skipIfSeen) {
                                blocks.filter { it !in seenBlockMap }
                            } else {
                                blocks
                            }
                        }

                    Pair(seenBlockMap, availableBlocks)
                }

                availableBlocks.map { height: Long -> Pair(height, seenBlockMap[height]) }
            }
            .flowOn(dispatchers.io())
            .flatMapMerge(options.concurrency) { queryBlocks(it) }
            .flowOn(dispatchers.io())
            .map { Either.Right(it.copy(historical = true)) }
        //.catch { e -> Either.Left(e) }
    }

    /**
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    suspend fun streamLiveBlocks(): Flow<Either<Throwable, StreamBlock>> {
        // Toggle the Lifecycle register start state :
        eventStreamService.startListening()

        return flow {
            for (event in eventStreamService.observeWebSocketEvent()) {
                when (event) {
                    is WebSocket.Event.OnConnectionOpened<*> -> {
                        log.info("streamLiveBlocks::received OnConnectionOpened event")
                        log.info("streamLiveBlocks::initializing subscription for tm.event='NewBlock'")
                        eventStreamService.subscribe(Subscribe("tm.event='NewBlock'"))
                    }
                    is WebSocket.Event.OnMessageReceived -> {
                        when (event.message) {
                            is Message.Text -> {
                                val message = event.message as Message.Text
                                val messageType = deserializeMessage(message.value)
                                if (messageType != null) {
                                    when (messageType) {
                                        is MessageType.Empty -> {
                                            log.info("received empty ACK message => ${message.value}")
                                        }
                                        is MessageType.NewBlock -> {
                                            val streamBlock: StreamBlock? =
                                                messageType.block.data.value.block.let {
                                                    queryBlock(Either.Right(it), skipIfNoTxs = false)
                                                }
                                            if (streamBlock != null) {
                                                emit(Either.Right(streamBlock))
                                            }
                                        }
                                    }
                                }
                            }
                            is Message.Bytes -> {
                                // ignore; binary payloads not supported:
                                log.warn("streamLiveBlocks::binary message payload not supported")
                            }
                        }
                    }
                    is WebSocket.Event.OnConnectionFailed -> {
                        emit(Either.Left(event.throwable))
                    }
                    else -> {
                        emit(Either.Left(Throwable("streamLiveBlocks::unexpected event type: ${event.toString()}")))
                    }
                }
            }
        }
            .onStart {
                log.info("streamLiveBlocks::thread<${Thread.currentThread().id}>")
            }
            .retryWhen { cause: Throwable, attempt: Long ->
                log.info("streamLiveBlocks::recovering Flow (attempt ${attempt + 1})")
                when (cause) {
                    is JsonDataException -> {
                        log.warn("streamLiveBlocks::parse error: $cause")
                        true
                    }
                    else -> false
                }
            }
    }

    /**
     * Constructs a Flow of live and historical blocks, plus associated event data.
     *
     * If a starting height is provided, historical blocks will be included in the Flow from the starting height, up
     * to the latest block height determined at the start of the collection of the Flow.
     *
     * @return A Flow of live and historical blocks, plus associated event data.
     */
    override suspend fun streamBlocks(): Flow<Either<Throwable, StreamBlock>> =
        if (options.fromHeight != null) {
            log.info("Listening for live and historical blocks from height ${options.fromHeight}")
            merge(streamHistoricalBlocks(), streamLiveBlocks())
        } else {
            log.info("Listening for live blocks only")
            streamLiveBlocks()
        }
}

