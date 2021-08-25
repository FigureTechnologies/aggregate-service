package io.provenance.aggregate.service.stream

import arrow.core.Either
import io.provenance.aggregate.service.stream.extensions.blockEvents
import io.provenance.aggregate.service.stream.extensions.txEvents
import io.provenance.aggregate.service.stream.extensions.txHash
import io.provenance.aggregate.service.stream.models.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.service.logger
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

interface ABCIApiClient {
    suspend fun abciInfo(): ABCIInfoResponse
}

interface InfoApiClient {
    suspend fun block(height: Long?): BlockResponse
    suspend fun blockResults(height: Long?): BlockResultsResponse
    suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse
}

open class EventStream(
    private val lifecycle: LifecycleRegistry,
    private val eventStreamService: EventStreamService,
    private val abciApi: ABCIApiClient,
    private val infoApi: InfoApiClient,
    private val batchSize: Int = 4,
    private val skipIfEmpty: Boolean = true
) {
    companion object {
        private const val TENDERMINT_MAX_QUERY_RANGE = 20
    }

    protected val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val log = logger()

    private fun getBlockHeightRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
        val step = TENDERMINT_MAX_QUERY_RANGE
        return sequence {
            var i = minHeight
            var j = i + step
            while (j <= maxHeight) {
                yield(Pair(i, j))
                i = j + 1
                j = i + step
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
            is Either.Left<Long> -> infoApi.block(heightOrBlock.value).result?.block
            is Either.Right<Block> -> heightOrBlock.value
        }

        if (skipIfNoTxs && block?.data?.txs?.size ?: 0 == 0) {
            return null
        }

        return block?.run {
            val blockResponse = infoApi.blockResults(block.header?.height).result
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
            infoApi.blockchain(minHeight, maxHeight)
                .result
                ?.blockMetas
                .let {
                    if (skipIfEmpty) {
                        it?.filter { it.numTxs ?: 0 > 0 }
                    } else {
                        it
                    }
                }
                ?.map { it.header!!.height }
                ?: emptyList()

        // Chunk up the heights of returned blocks, then for the heights in each block,
        // concurrently fetch the events for each block at the given height:
        return blockHeightsInRange
            .chunked(batchSize)
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

    @kotlinx.coroutines.FlowPreview
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    protected suspend fun streamHistoricalBlocks(fromHeight: Long): Flow<Either<Throwable, StreamBlock>> {
        val lastBlockHeight: Long = abciApi.abciInfo().result?.response?.lastBlockHeight ?: 0
        return getBlockHeightRanges(fromHeight, lastBlockHeight - 1)
            .chunked(batchSize)
            .asFlow()
            .flatMapConcat { heightRanges ->
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        heightRanges.map { (minHeight: Long, maxHeight: Long) ->
                            async {
                                //log.info("streamHistoricalBlocks::async<${Thread.currentThread().id}>")
                                queryBlockRange(minHeight, maxHeight).onStart {
                                    log.info("streamHistoricalBlocks::querying range ($minHeight, $maxHeight")
                                }
                            }
                        }
                    }
                }
                    .awaitAll()
                    .merge()
            }
            .map { Either.Right(it.copy(historical = true)) }
            .catch { e -> Either.Left(e) }
    }

    suspend fun streamLiveBlocks(): Flow<Either<Throwable, StreamBlock>> {
        lifecycle.onNext(Lifecycle.State.Started)
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

