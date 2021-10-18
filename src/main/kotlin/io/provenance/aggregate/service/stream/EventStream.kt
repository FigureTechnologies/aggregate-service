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
import io.provenance.aggregate.service.aws.dynamodb.client.DynamoClient
import io.provenance.aggregate.service.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.models.*
import io.provenance.aggregate.service.stream.models.extensions.blockEvents
import io.provenance.aggregate.service.stream.models.extensions.txEvents
import io.provenance.aggregate.service.stream.models.extensions.txHash
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.extensions.dateTime
import io.provenance.aggregate.service.stream.models.rpc.request.Subscribe
import io.provenance.aggregate.service.stream.models.rpc.response.MessageType
import io.provenance.aggregate.service.utils.backoff
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime

typealias HeightGetter = suspend () -> Long?

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintServiceClient: TendermintServiceClient,
    private val dynamo: DynamoClient,
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
        val fromHeight: Either<Long, HeightGetter>?,
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
            private var fromHeight: Either<Long, HeightGetter>? = null
            private var toHeight: Long? = null
            private var skipIfEmpty: Boolean = true
            private var skipIfSeen: Boolean = false
            private var blockEventPredicate: ((event: String) -> Boolean)? = null
            private var txEventPredicate: ((event: String) -> Boolean)? = null

            /**
             * Sets the concurrency level when merging disparate streams of block data.
             *
             * @property level The concurrency level.
             */
            fun concurrency(level: Int) = apply { concurrency = level }

            /**
             * Sets the maximum number of blocks that will be fetched and processed concurrently.
             *
             * @property size The batch size.
             */
            fun batchSize(size: Int) = apply { batchSize = size }

            /**
             * Sets the lowest height to fetch historical blocks from.
             *
             * If no minimum height is provided, only live blocks will be streamed.
             *
             * @property height The minimum height to fetch blocks from.
             */
            fun fromHeight(height: Long?) = apply { fromHeight = if (height != null) Either.Left(height) else null }
            fun fromHeight(height: Long) = apply { fromHeight = Either.Left(height) }

            /**
             * Provide a function that returns the lowest height to fetch historical blocks from.
             *
             * If no minimum height is provided, only live blocks will be streamed.
             *
             * @property computeHeight A function that computes the minimum height to fetch blocks from.
             */
            fun fromHeight(computeHeight: HeightGetter) = apply { fromHeight = Either.Right(computeHeight) }

            /**
             * Sets the highest height to fetch historical blocks to. If no maximum height is provided, blocks will
             * be fetched up to the latest height, as resulted by the ABCIInfo endpoint.
             *
             * @property height The maximum height to fetch blocks to.
             */
            fun toHeight(height: Long?) = apply { toHeight = height }
            fun toHeight(height: Long) = apply { toHeight = height }

            /**
             * Toggles skipping blocks that contain no transaction data.
             *
             * @property value If true, blocks that contain no transaction data will not be processed.
             */
            fun skipIfEmpty(value: Boolean) = apply { skipIfEmpty = value }

            /**
             * Toggles skipping blocks that have been previously uploaded and subsequently tracked.
             *
             * @property value If true, blocks that have already been processed will not be processed again.
             */
            fun skipIfSeen(value: Boolean) = apply { skipIfSeen = value }

            /**
             * Filter blocks by one or more specific block events (case-insensitive).
             * Only blocks possessing the specified block event(s) will be streamed.
             *
             * @property predicate If evaluates to true will include the given block for processing.
             */
            fun matchBlockEvent(predicate: (event: String) -> Boolean) =
                apply { blockEventPredicate = predicate }

            /**
             * Filter blocks by one or more specific transaction events (case-insensitive).
             * Only blocks possessing the specified transaction event(s) will be streamed.
             *
             * @property predicate If evaluated to true will include the given block for processing.
             */
            fun matchTxEvent(predicate: (event: String) -> Boolean) = apply { txEventPredicate = predicate }

            /**
             * @return An Options instance used to construct an event stream
             */
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
        private val tendermintServiceClient: TendermintServiceClient,
        private val dynamoClient: DynamoClient,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    ) {
        private fun noop(_options: Options.Builder) {}

        fun create(setOptions: (options: Options.Builder) -> Unit = ::noop): EventStream {
            val optionsBuilder = Options.Builder()
                .batchSize(config.eventStream.batchSize)
                .skipIfEmpty(true)
            setOptions(optionsBuilder)
            return create(optionsBuilder.build())
        }

        fun create(options: Options): EventStream {
            val lifecycle = LifecycleRegistry(config.eventStream.websocket.throttleDurationMs)
            val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
            val tendermintRpc: TendermintRPCStream = scarlet.create()
            val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)

            return EventStream(
                eventStreamService,
                tendermintServiceClient,
                dynamoClient,
                moshi,
                options = options,
                dispatchers = dispatchers
            )
        }
    }

    private val log = logger()

    private val responseMessageDecoder = MessageType.Decoder(moshi)

    /**
     * A serializer function that converts a [StreamBlock] instance to a JSON string.
     *
     * @return (StreamBlock) -> String
     */
    val serializer: (StreamBlock) -> String =
        { block: StreamBlock -> moshi.adapter(StreamBlock::class.java).toJson(block) }

    /**
     * Computes and returns the starting height (if it can be determined) to be used when streaming historical blocks.
     *
     * @return Long? The starting block height to use, if it exists.
     */
    private suspend fun getStartingHeight(): Long? =
        options.fromHeight
            ?.let {
                when (it) {
                    is Either.Left -> it.value     // Constant value
                    is Either.Right -> it.value()  // Computed value
                    else -> null
                }
            }

    /**
     * Computes and returns the ending height (if it can be determined) tobe used when streaming historical blocks.
     *
     * @return Long? The ending block height to use, if it exists.
     */
    private suspend fun getEndingHeight(): Long? =
        options.toHeight
            ?: tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight

    /**
     * Returns a sequence of block height pairs [[low, high]], representing a range to query when searching for blocks.
     */
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
     * Returns the heights of all existing blocks in a height range [[low, high]], subject to certain conditions.
     *
     * - If [Options.skipIfEmpty] is true, only blocks which contain 1 or more transactions will be returned.
     *
     * @return A list of block heights
     */
    private suspend fun getBlockHeightsInRange(minHeight: Long, maxHeight: Long): List<Long> {
        if (minHeight > maxHeight) {
            return emptyList()
        }

        // invariant
        assert((maxHeight - minHeight) <= TENDERMINT_MAX_QUERY_RANGE) {
            "Difference between (minHeight, maxHeight) can be at maximum $TENDERMINT_MAX_QUERY_RANGE"
        }

        return (tendermintServiceClient.blockchain(minHeight, maxHeight)
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
     *
     * @return True or false if [Options.blockEventPredicate] matches a block-level event associated with a block.
     * If the return value is null, then [Options.blockEventPredicate] was never set.
     */
    private fun <T : EncodedBlockchainEvent> matchesBlockEvent(blockEvents: Iterable<T>): Boolean? =
        options.blockEventPredicate?.let { p -> blockEvents.any { p(it.eventType) } }

    /**
     * Test if any transaction events match the supplied predicate.
     *
     * @return True or false if [Options.txEventPredicate] matches a transaction-level event associated with a block.
     * If the return value is null, then [Options.txEventPredicate] was never set.
     */
    private fun <T : EncodedBlockchainEvent> matchesTxEvent(txEvents: Iterable<T>): Boolean? =
        options.txEventPredicate?.let { p -> txEvents.any { p(it.eventType) } }

    /**
     * Query a block by height, returning any events associated with the block.
     *
     *  @param heightOrBlock Fetch a block, plus its events, by its height or the `Block` model itself.
     *  @param skipIfNoTxs If [skipIfNoTxs] is true, if the block at the given height has no transactions, null will
     *  be returned in its place.
     */
    private suspend fun queryBlock(
        heightOrBlock: Either<Long, Block>,
        skipIfNoTxs: Boolean = true
    ): StreamBlock? {
        val block: Block? = when (heightOrBlock) {
            is Either.Left<Long> -> tendermintServiceClient.block(heightOrBlock.value).result?.block
            is Either.Right<Block> -> heightOrBlock.value
        }

        if (skipIfNoTxs && block?.data?.txs?.size ?: 0 == 0) {
            return null
        }

        return block?.run {
            val blockDatetime = header?.dateTime()
            val blockResponse = tendermintServiceClient.blockResults(header?.height).result
            val blockEvents: List<BlockEvent> = blockResponse.blockEvents(blockDatetime)
            val txEvents: List<TxEvent> = blockResponse.txEvents(blockDatetime) { index: Int -> txHash(index) ?: "" }
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
     * @param blockHeights The heights of the blocks to query, along with optional metadata to attach to the fetched
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
     * Constructs a Flow of historical blocks and associated events based on a starting height.
     *
     * Blocks will be streamed from the given starting height up to the latest block height,
     * as determined by the start of the Flow.
     *
     * If no ending height could be found, an exception will be raised.
     *
     * @return A flow of historical blocks
     */
    fun streamHistoricalBlocks(): Flow<StreamBlock> = flow {
        val startHeight: Long = getStartingHeight() ?: 0
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(streamHistoricalBlocks(startHeight, endHeight))
    }

    private fun streamHistoricalBlocks(startHeight: Long): Flow<StreamBlock> = flow {
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(streamHistoricalBlocks(startHeight, endHeight))
    }

    private fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlock> =
        flow {
            log.info("historical::streaming blocks from $startHeight to $endHeight")
            log.info("historical::batch size = ${options.batchSize}")

            // Since each pair will be TENDERMINT_MAX_QUERY_RANGE apart, and we want the cumulative number of heights
            // to query to be DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS, we need
            // floor(max(TENDERMINT_MAX_QUERY_RANGE, DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS) / min(TENDERMINT_MAX_QUERY_RANGE, DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS))
            // chunks:
            val xValue = TENDERMINT_MAX_QUERY_RANGE.toDouble()
            val yValue = DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS.toDouble()
            val numChunks: Int = floor(max(xValue, yValue) / min(xValue, yValue)).toInt()

            emitAll(
                getBlockHeightQueryRanges(startHeight, endHeight)
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
                            async { getBlockHeightsInRange(minHeight, maxHeight) }
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
                                when (val type = responseMessageDecoder.decode(message.value)) {
                                    is MessageType.Empty ->
                                        log.info("received empty ACK message => ${message.value}")
                                    is MessageType.NewBlock -> {
                                        val streamBlock: StreamBlock? =
                                            type.block.data.value.block.let {
                                                log.info("live::received NewBlock message: #${it.header?.height}")
                                                queryBlock(Either.Right(it), skipIfNoTxs = false)
                                            }
                                        if (streamBlock != null) {
                                            emit(streamBlock)
                                        }
                                    }
                                    is MessageType.Error -> {

                                    }
                                    is MessageType.Panic -> {
                                        log.error("Upstream panic from RPC endpoint: ${type.error}")
                                        throw CancellationException("RPC endpoint panic: ${type.error}")
                                    }
                                    is MessageType.Unknown ->
                                        log.info("unknown message type; skipping message => ${message.value}")

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
    fun streamBlocks(): Flow<StreamBlock> = flow {
        val startingHeight: Long? = getStartingHeight()
        emitAll(
            if (startingHeight != null) {
                log.info("Listening for live and historical blocks from height $startingHeight")
                merge(streamHistoricalBlocks(startingHeight), streamLiveBlocks())
            } else {
                log.info("Listening for live blocks only")
                streamLiveBlocks()
            }
        )
    }
        .cancellable()
        .retryWhen { cause: Throwable, attempt: Long ->
            log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})")
            when (cause) {
                is EOFException,
                is CompletionException,
                is ConnectException,
                is SocketTimeoutException,
                is SocketException -> {
                    val duration = backoff(attempt, jitter = false)
                    log.error("Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                    delay(duration)
                    true
                }
                else -> false
            }
        }
}

