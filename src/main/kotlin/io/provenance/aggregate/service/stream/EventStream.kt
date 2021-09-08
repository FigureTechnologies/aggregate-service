package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.*
import com.tinder.scarlet.Message
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.service.Config
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.Event
import io.provenance.aggregate.service.stream.models.extensions.blockEvents
import io.provenance.aggregate.service.stream.models.extensions.isEmpty
import io.provenance.aggregate.service.stream.models.extensions.txEvents
import io.provenance.aggregate.service.stream.models.extensions.txHash
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class StreamBlock(
    val block: Block,
    val blockEvents: List<BlockEvent>,
    val txEvents: List<TxEvent>,
    val historical: Boolean = false
) {
    val height: Long? get() = block.header?.height
}

@JsonClass(generateAdapter = true)
data class BlockEvent(
    val height: Long,
    val eventType: String,
    val attributes: List<Event>,
)

@JsonClass(generateAdapter = true)
data class TxEvent(
    val height: Long,
    val txHash: String,
    val eventType: String,
    val attributes: List<Event>,
)

class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintService: TendermintService,
    private val moshi: Moshi,
    private val batchSize: Int = 4,
    private val skipIfEmpty: Boolean = true
) {
    companion object {
        const val TENDERMINT_MAX_QUERY_RANGE = 20
    }

    sealed class MessageType {
        object Empty : MessageType()
        data class NewBlock(val block: NewBlockResult) : MessageType()
    }

    data class Factory(
        private val config: Config,
        private val moshi: Moshi,
        private val eventStreamBuilder: Scarlet.Builder,
        private val tendermintService: TendermintService
    ) {
        fun getStream(skipEmptyBlocks: Boolean = true): EventStream {
            val lifecycle = LifecycleRegistry(config.event.stream.throttleDurationMs)
            val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
            val tendermintRpc: TendermintRPCStream = scarlet.create()
            val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)
            return EventStream(
                eventStreamService,
                tendermintService,
                moshi,
                batchSize = config.event.stream.batchSize,
                skipIfEmpty = skipEmptyBlocks
            )
        }
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
     * Query a range of blocks by a given minimum and maximum height range (inclusive), returning a io.provenance.aggregate.service.flow of
     * found historical blocks along with events associated with each block, if any.
     *
     * @param minHeight The minimum block height in the query range, inclusive.
     * @param maxHeight The maximum block height in the query range, inclusive.
     * @return A io.provenance.aggregate.service.flow of found historical blocks along with events associated with each block, if any.
     */
    private suspend fun queryBlockRange(minHeight: Long, maxHeight: Long): Flow<StreamBlock> {
        if (minHeight > maxHeight) {
            return emptyFlow()
        }

        // Only take blocks with transactions:
        val blockHeightsInRange: List<Long> =
            (tendermintService.blockchain(minHeight, maxHeight)
                .result
                ?.blockMetas
                .let {
                    if (skipIfEmpty) {
                        it?.filter { it.numTxs ?: 0 > 0 }
                    } else {
                        it
                    }
                }
                ?.map { it.header?.height }
                ?.filterNotNull()
                ?: emptyList<Long>())
                .sortedWith(naturalOrder<Long>())

        if (blockHeightsInRange.isEmpty()) {
            //log.info("No block found in range ($minHeight, $maxHeight)")
            return emptyFlow()
        }

        log.info("Found ${blockHeightsInRange.size} block(s) in range ($minHeight, $maxHeight) = $blockHeightsInRange")

        // Chunk up the heights of returned blocks, then for the heights in each block,
        // concurrently fetch the events for each block at the given height:
        return blockHeightsInRange.chunked(batchSize)
            .asFlow()
            .transform { chunkOfHeights: List<Long> ->
                val blocks: List<StreamBlock> = withContext(Dispatchers.IO) {
                    coroutineScope {
                        chunkOfHeights.map { height: Long ->
                            async {
                                //log.info("streamHistoricalBlocks::queryBlockRange::async<${Thread.currentThread().id}>")
                                queryBlock(Either.Left(height), skipIfNoTxs = skipIfEmpty)
                            }
                        }
                    }.awaitAll()
                        .filterNotNull()
                }
                for (block in blocks) {
                    emit(block)
                }
            }
    }

    /**
     * Constructs a io.provenance.aggregate.service.flow of historical blocks and associated events based on a starting height. Blocks will be streamed
     * from the given starting height up to the latest block height, as determined by the start of the io.provenance.aggregate.service.flow.
     *
     * @param fromHeight Stream blocks from the given starting height.
     * @param concurrency The concurrency limit for the io.provenance.aggregate.service.flow's `flatMapMerge` operation. See
     * https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.io.provenance.aggregate.service.flow/flat-map-merge.html
     * @return A io.provenance.aggregate.service.flow of historical blocks and associated events
     */
    @kotlinx.coroutines.FlowPreview
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    suspend fun streamHistoricalBlocks(
        fromHeight: Long,
        concurrency: Int = DEFAULT_CONCURRENCY
    ): Flow<Either<Throwable, StreamBlock>> {
        val lastBlockHeight: Long = tendermintService.abciInfo().result?.response?.lastBlockHeight ?: 0
        log.info("streamHistoricalBlocks::streaming blocks: $fromHeight to $lastBlockHeight")
        log.info("streamHistoricalBlocks::batch size = $batchSize")
        return getBlockHeightQueryRanges(fromHeight, lastBlockHeight)
            .asFlow()
            .flatMapMerge(concurrency) { (minHeight: Long, maxHeight: Long) ->
                queryBlockRange(minHeight, maxHeight).onStart {
                    //log.info("streamHistoricalBlocks::querying range ($minHeight, $maxHeight)")
                }
            }
            .map {
                Either.Right(it.copy(historical = true))
            }
            .catch { e -> Either.Left(e) }
    }

    /**
     * Constructs a io.provenance.aggregate.service.flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A io.provenance.aggregate.service.flow of newly minted blocks and associated events
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
                log.info("streamLiveBlocks::recovering io.provenance.aggregate.service.flow (attempt ${attempt + 1})")
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
     * Constructs a io.provenance.aggregate.service.flow of live and historical blocks, plus associated event data.
     *
     * If a starting height is provided, historical blocks will be included in the io.provenance.aggregate.service.flow from the starting height, up
     * to the latest block height determined at the start of the collection of the io.provenance.aggregate.service.flow.
     *
     * @param fromHeight The optional block height to start streaming from for historical block data.
     * @param concurrency The concurrency limit for the io.provenance.aggregate.service.flow's `flatMapMerge` operation. See
     * https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.io.provenance.aggregate.service.flow/flat-map-merge.html
     * @return A io.provenance.aggregate.service.flow of live and historical blocks, plus associated event data.
     */
    @kotlinx.coroutines.FlowPreview
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    suspend fun streamBlocks(
        fromHeight: Long? = null,
        concurrency: Int = DEFAULT_CONCURRENCY
    ): Flow<Either<Throwable, StreamBlock>> {
        return if (fromHeight != null) {
            merge(streamHistoricalBlocks(fromHeight, concurrency), streamLiveBlocks())
        } else {
            streamLiveBlocks()
        }
    }
}

