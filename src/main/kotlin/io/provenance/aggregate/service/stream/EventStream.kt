package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.*
import com.tinder.scarlet.Message
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.service.Config
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.extensions.blockEvents
import io.provenance.aggregate.service.stream.extensions.txEvents
import io.provenance.aggregate.service.stream.extensions.txHash
import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@JsonClass(generateAdapter = true)
data class StreamBlock(
    val block: Block,
    val blockEvents: List<BlockEvent>,
    val txEvents: List<TxEvent>,
    val historical: Boolean = false
)

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

open class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintService: TendermintService,
    private val moshi: Moshi,
    private val batchSize: Int = 4,
    private val skipIfEmpty: Boolean = true
) {
    companion object {
        const val TENDERMINT_MAX_QUERY_RANGE = 20
    }

    data class Factory(
        private val config: Config,
        private val moshi: Moshi,
        private val eventStreamBuilder: Scarlet.Builder,
        private val tendermintService: TendermintService
    ) {
        fun getStream(skipEmptyBlocks: Boolean = true): EventStream {
            val lifecycle = LifecycleRegistry(config.event.stream.throttle_duration_ms)
            val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
            val tendermintRpc: TendermintRPCStream = scarlet.create()
            val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)
            return EventStream(
                eventStreamService,
                tendermintService,
                moshi,
                batchSize = config.event.stream.batch_size,
                skipIfEmpty = skipEmptyBlocks
            )
        }
    }

    private val log = logger()

    private fun getBlockHeightQueryRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
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

        log.info("Found blocks in range ($minHeight, $maxHeight) = $blockHeightsInRange")

        // Chunk up the heights of returned blocks, then for the heights in each block,
        // concurrently fetch the events for each block at the given height:
        val chunked = blockHeightsInRange.chunked(batchSize)

        log.info("($minHeight, $maxHeight) chunks = $chunked")

        return chunked
            .asFlow()
            .transform { chunkOfHeights: List<Long> ->
                val blocks: List<StreamBlock> = withContext(Dispatchers.Default) {
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
     * Serialize data of a given class type
     */
    fun <T> serialize(clazz: Class<T>, data: T) = moshi.adapter<T>(clazz).toJson(data)

    @kotlinx.coroutines.FlowPreview
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    suspend fun streamHistoricalBlocks(
        fromHeight: Long,
        concurrency: Int = DEFAULT_CONCURRENCY
    ): Flow<Either<Throwable, StreamBlock>> {
        val lastBlockHeight: Long = tendermintService.abciInfo().result?.response?.lastBlockHeight ?: 0
        log.info("streamHistoricalBlocks::streaming blocks: $fromHeight to $lastBlockHeight")
        return getBlockHeightQueryRanges(fromHeight, lastBlockHeight)
            .asFlow()
            .flatMapMerge(concurrency) { (minHeight: Long, maxHeight: Long) ->
                queryBlockRange(minHeight, maxHeight).onStart {
                    log.info("streamHistoricalBlocks::querying range ($minHeight, $maxHeight)")
                }
            }
            .map {
                Either.Right(it.copy(historical = true))
            }
            .catch { e -> Either.Left(e) }
    }

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
                                // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
                                // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
                                // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
                                val rpcResponse: JsonAdapter<RpcResponse<NewBlockResult>> = moshi.adapter(
                                    Types.newParameterizedType(
                                        RpcResponse::class.java,
                                        NewBlockResult::class.java
                                    )
                                )
                                val newBlockEventResponse: RpcResponse<NewBlockResult>? =
                                    rpcResponse.fromJson(message.value)
                                if (newBlockEventResponse != null) {
                                    val streamBlock: StreamBlock? =
                                        newBlockEventResponse.result?.data?.value?.block?.let {
                                            queryBlock(Either.Right(it), skipIfNoTxs = false)
                                        }
                                    if (streamBlock != null) {
                                        emit(Either.Right(streamBlock))
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
                log.info("streamLiveBlocks::recovering flow (attempt ${attempt + 1})")
                when (cause) {
                    is JsonDataException -> {
                        log.warn("streamLiveBlocks::parse error: $cause")
                        true
                    }
                    else -> false
                }
            }
    }

    @kotlinx.coroutines.FlowPreview
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    suspend fun streamBlocks(fromHeight: Long? = null): Flow<Either<Throwable, StreamBlock>> {
        return if (fromHeight != null) {
            merge(streamHistoricalBlocks(fromHeight), streamLiveBlocks())
        } else {
            streamLiveBlocks()
        }
    }
}

