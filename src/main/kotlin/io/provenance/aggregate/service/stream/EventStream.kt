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
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintService: TendermintService,
    private val dynamo: AwsDynamoInterface,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val options: Options = Options.DEFAULT
) {

    companion object {
        const val DEFAULT_BATCH_SIZE = 8
        const val TENDERMINT_MAX_QUERY_RANGE = 20
        const val DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS = 100
    }

    data class Options(
        val concurrency: Int,
        val batchSize: Int,
        val fromHeight: Long?,
        val toHeight: Long?,
        val skipIfEmpty: Boolean,
        val skipIfSeen: Boolean,
        val blockEventPredicate: ((event: String) -> Boolean)?,
        val txEventPredicate: ((event: String) -> Boolean)?
    ) {
        companion object {
            val DEFAULT: Options = Builder().build()
            fun builder() = Builder()
        }

        class Builder {
            private var concurrency: Int = DEFAULT_CONCURRENCY
            private var batchSize: Int = DEFAULT_BATCH_SIZE
            private var fromHeight: Long? = null
            private var toHeight: Long? = null
            private var skipIfEmpty: Boolean = true
            private var skipIfSeen: Boolean = false
            private var blockEventPredicate: ((event: String) -> Boolean)? = null
            private var txEventPredicate: ((event: String) -> Boolean)? = null

            /**
             * Sets the concurrency level when merging disparate streams of block data.
             */
            fun concurrency(value: Int) = apply { concurrency = value }

            /**
             * Sets the maximum number of blocks that will be fetched concurrently.
             */
            fun batchSize(size: Int) = apply { batchSize = size }

            /**
             * Sets the lowest height to fetch historical blocks from. If no minimum height is provided, only
             * live blocks will be streamed.
             */
            fun fromHeight(height: Long?) = apply { fromHeight = height }
            fun fromHeight(height: Long) = apply { fromHeight = height }

            /**
             * Sets the highest height to fetch historical blocks to. If no maximum height is provided, blocks will
             * be fetched up to the latest height, as resulted by the ABCIInfo endpoint.
             */
            fun toHeight(height: Long?) = apply { toHeight = height }
            fun toHeight(height: Long) = apply { toHeight = height }

            /**
             * Toggles skipping blocks that contain no transaction data.
             */
            fun skipIfEmpty(value: Boolean) = apply { skipIfEmpty = value }

            /**
             * Toggles skipping blocks that have been previously uploaded and subsequently tracked.
             */
            fun skipIfSeen(value: Boolean) = apply { skipIfSeen = value }

            /**
             * Filter blocks by one or more specific block events (case-insensitive).
             * Only blocks possessing the specified block event(s) will be streamed.
             */
            fun matchBlockEvent(predicate: (event: String) -> Boolean) =
                apply { blockEventPredicate = predicate }

            /**
             * Filter blocks by one or more specific transaction events (case-insensitive).
             * Only blocks possessing the specified transaction event(s) will be streamed.
             */
            fun matchTxEvent(predicate: (event: String) -> Boolean) = apply { txEventPredicate = predicate }

            fun build(): Options = Options(
                concurrency = concurrency,
                batchSize = batchSize,
                fromHeight = fromHeight,
                toHeight = toHeight,
                skipIfEmpty = skipIfEmpty,
                skipIfSeen = skipIfSeen,
                blockEventPredicate = blockEventPredicate,
                txEventPredicate = txEventPredicate
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
        private fun noop(_options: Options.Builder) {}

        fun create(setOptions: (options: Options.Builder) -> Unit = ::noop): EventStream {
            val optionsBuilder = Options.Builder()
                .batchSize(config.event.stream.batchSize)
                .skipIfEmpty(true)
            setOptions(optionsBuilder)
            return create(optionsBuilder.build())
        }

        fun create(options: Options): EventStream {
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

    sealed interface MessageType {
        object Empty : MessageType
        data class NewBlock(val block: NewBlockResult) : MessageType
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
     * Test if any block events match the supplied predicate.
     */
    private fun <T : EncodedBlockchainEvent> matchesBlockEvent(blockEvents: Iterable<T>): Boolean? =
        options.blockEventPredicate?.let { p -> blockEvents.any { p(it.eventType) } }

    /**
     * Test if any transaction events match the supplied predicate.
     */
    private fun <T : EncodedBlockchainEvent> matchesTxEvent(txEvents: Iterable<T>): Boolean? =
        options.txEventPredicate?.let { p -> txEvents.any { p(it.eventType) } }

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
            val txEvents: List<TxEvent> = blockResponse.txEvents { index: Int -> txHash(index) ?: "" }
            val streamBlock = StreamBlock(this, blockEvents, txEvents)
            val matchBlock = matchesBlockEvent(blockEvents)
            val matchTx = matchesTxEvent(txEvents)
            // ugly:
            if ((matchBlock == null && matchTx == null)
                || (matchBlock == null && matchTx != null && matchTx)
                || (matchBlock != null && matchBlock && matchTx == null)
                || (matchBlock != null && matchBlock && matchTx != null && matchTx)
            ) {
                streamBlock
            } else {
                null
            }
        }
    }

    @JvmName("queryBlocksNoMetadata")
    private fun queryBlocks(blockHeights: Iterable<Long>): Flow<StreamBlock> =
        queryBlocks(blockHeights.map { Pair(it, null) })

    /***
     * Query a collections of blocks by their heights.
     *
     * Note: it is assumed the specified blocks already exists. No check will be performed to verify existence!
     *
     * @param blockHeights The heights of the blocks to query, along with optional metadata to attach to the fetched\
     *  block data.
     * @return A Flow of found historical blocks along with events associated with each block, if any.
     */
    private fun queryBlocks(blockHeights: Iterable<Pair<Long, BlockStorageMetadata?>>): Flow<StreamBlock> {
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
                                ?.let { it.copy(metadata = metadata) }
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
        // Chunk up the heights of returned blocks, then for the heights in each block,
        // concurrently fetch the events for each block at the given height:
//        return channelFlow {
//            for ((height, metadata) in blockHeights) {
//                launch {
//                    //log.info("querying block #${height}")
//                    val block = queryBlock(Either.Left(height), skipIfNoTxs = options.skipIfEmpty)
//                        ?.let {
//                            it.copy(metadata = metadata)
//                        }
//                    if (block != null) {
//                        send(block)
//                    }
//                }
//            }
//        }
//            .flowOn(dispatchers.io())
    }

    /**
     * Constructs a Flow of historical blocks and associated events based on a starting height. Blocks will be streamed
     * from the given starting height up to the latest block height, as determined by the start of the Flow.
     *
     * @param fromHeight Stream blocks from the given starting height.
     * @param concurrency The concurrency limit for the Flow's `flatMapMerge` operation. See
     * https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.Flow/flat-map-merge.html
     * @return A Flow of historical blocks and associated events
     */
    fun streamHistoricalBlocks(): Flow<StreamBlock> =
        flow {
            val startingHeight: Long = options.fromHeight ?: error("No starting height set")
            val lastBlockHeight: Long = tendermintService.abciInfo().result?.response?.lastBlockHeight ?: 0

            log.info("historical::streaming blocks: $startingHeight to $lastBlockHeight")
            log.info("historical::batch size = ${options.batchSize}")

            // Since each pair will be TENDERMINT_MAX_QUERY_RANGE apart, and we want the cumulative number of heights
            // to query to be DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS, we need
            // floor(max(TENDERMINT_MAX_QUERY_RANGE, DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS) / min(TENDERMINT_MAX_QUERY_RANGE, DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS))
            // chunks:
            val xValue = TENDERMINT_MAX_QUERY_RANGE.toDouble()
            val yValue = DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS.toDouble()
            val numChunks: Int = floor(max(xValue, yValue) / min(xValue, yValue)).toInt()

            emitAll(
                getBlockHeightQueryRanges(startingHeight, lastBlockHeight)
                    .chunked(numChunks)
                    .asFlow()
            )
        }
            .map { heightPairChunk: List<Pair<Long, Long>> -> // each pair will be `TENDERMINT_MAX_QUERY_RANGE` units apart

                val lowest = heightPairChunk.minOf { it.first }
                val highest = heightPairChunk.maxOf { it.second }
                val fullBlockHeights: Set<Long> = (lowest..highest).toSet()

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

                    log.info("${availableBlocks.size} block(s) in [$lowest..$highest]")

                    Pair(seenBlockMap, availableBlocks)
                }

                availableBlocks.map { height: Long -> Pair(height, seenBlockMap[height]) }
            }
            .flowOn(dispatchers.io())
            .flatMapMerge(options.concurrency) { queryBlocks(it) }
            .flowOn(dispatchers.io())
            .map { it.copy(historical = true) }

    /**
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    fun streamLiveBlocks(): Flow<StreamBlock> {

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
                    is WebSocket.Event.OnMessageReceived ->
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
                                                    log.info("live::received NewBlock message: #${it.header?.height}")
                                                    queryBlock(Either.Right(it), skipIfNoTxs = false)
                                                }
                                            if (streamBlock != null) {
                                                emit(streamBlock)
                                            }
                                        }
                                    }
                                }
                            }
                            is Message.Bytes -> {
                                // ignore; binary payloads not supported:
                                log.warn("live::binary message payload not supported")
                            }
                        }
                    is WebSocket.Event.OnConnectionFailed -> throw event.throwable
                    else -> throw Throwable("live::unexpected event type: $event")
                }
            }
        }
            .onStart {
                log.info("live::starting")
            }
            .onEach {
                log.info("live::got block #${it.height}")
            }
            .onCompletion {
                eventStreamService.stopListening()
            }
            .retryWhen { cause: Throwable, attempt: Long ->
                log.warn("live::error; recovering Flow (attempt ${attempt + 1})")
                when (cause) {
                    is JsonDataException -> {
                        log.error("streamLiveBlocks::parse error, skipping: $cause")
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

    fun streamBlocks(): Flow<StreamBlock> {
        return if (options.fromHeight != null) {
            log.info("Listening for live and historical blocks from height ${options.fromHeight}")
            merge(streamHistoricalBlocks(), streamLiveBlocks())
        } else {
            log.info("Listening for live blocks only")
            streamLiveBlocks()
        }
            .cancellable()
            .retryWhen { cause: Throwable, attempt: Long ->
                log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})")
                when (cause) {
                    is CompletionException,
                    is ConnectException,
                    is SocketTimeoutException,
                    is SocketException -> {
                        val duration = Backoff.forAttempt(attempt)
                        log.error("Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                        delay(duration)
                        true
                    }
                    else -> false
                }
            }
    }
}

