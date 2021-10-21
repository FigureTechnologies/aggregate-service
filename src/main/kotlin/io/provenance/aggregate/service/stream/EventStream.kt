package io.provenance.aggregate.service.stream

import arrow.core.Either
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.common.Config
import io.provenance.aggregate.service.DefaultDispatcherProvider
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.common.aws.dynamodb.client.DynamoClient
import io.provenance.aggregate.common.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.*
import io.provenance.aggregate.common.models.extensions.blockEvents
import io.provenance.aggregate.common.models.extensions.txEvents
import io.provenance.aggregate.common.models.extensions.txHash
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.models.extensions.dateTime
import io.provenance.aggregate.service.stream.models.rpc.request.Subscribe
import io.provenance.aggregate.service.stream.models.rpc.response.MessageType
import io.provenance.aggregate.common.utils.backoff
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.channels.Channel as KChannel

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
        /**
         * The default number of blocks that will be contained in a batch.
         */
        const val DEFAULT_BATCH_SIZE = 8

        /**
         * The default timeout limit to wait when batching items for emitting the batch.
         */
        val DEFAULT_BATCH_TIMEOUT: Duration = Duration.seconds(10)

        /**
         * The maximum size of the query range for block heights allowed by the Tendermint API.
         * This means, for a given block height `H`, we can ask for blocks in the range [`H`, `H` + `TENDERMINT_MAX_QUERY_RANGE`].
         * Requesting a larger range will result in the API emitting an error.
         */
        const val TENDERMINT_MAX_QUERY_RANGE = 20

        /**
         * The maximum number of items that can be included in a batch write operation to DynamoDB, as imposed by
         * AWS.
         */
        const val DYNAMODB_BATCH_GET_ITEM_MAX_ITEMS = 100
    }

    data class Options(
        val concurrency: Int,
        val batchSize: Int,
        val batchTimeout: Duration?,
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

        fun withConcurrency(concurrency: Int) = this.copy(concurrency = concurrency)

        fun withBatchSize(size: Int) = this.copy(batchSize = size)

        fun withBatchTimeout(timeout: Duration?) = this.copy(batchTimeout = timeout)

        fun withFromHeight(height: Either<Long, HeightGetter>?) = this.copy(fromHeight = height)

        fun withToHeight(height: Long?) = this.copy(toHeight = height)

        fun withSkipIfEmpty(value: Boolean) = this.copy(skipIfEmpty = value)

        fun withSkipIfSeen(value: Boolean) = this.copy(skipIfSeen = value)

        fun withBlockEventPredicate(predicate: ((event: String) -> Boolean)?) =
            this.copy(blockEventPredicate = predicate)

        fun withTxEventPredicate(predicate: ((event: String) -> Boolean)?) = this.copy(txEventPredicate = predicate)

        class Builder {
            private var concurrency: Int = DEFAULT_CONCURRENCY
            private var batchSize: Int = DEFAULT_BATCH_SIZE
            private var batchTimeout: Duration? = DEFAULT_BATCH_TIMEOUT
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
             * Sets the default batch timeout limit to wait when batching items for emitting the batch.
             *
             * @property timeout The batch timeout duration.
             */
            fun batchTimeout(timeout: Duration?) = apply { batchTimeout = timeout }

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
                batchTimeout = batchTimeout,
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
                .batchSize(config.eventStream.batch.size)
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

    /**
     * Internal events used for communication between the event historic and live streams:
     */
    private sealed interface InternalStreamEvent {
        /**
         * Historical stream ran to completion successfully.
         */
        object HistoricStreamCompleted : InternalStreamEvent
    }

    /**
     * Logger.
     */
    private val log = logger()

    /**
     * A decoder for Tendermint RPC API messages.
     */
    private val responseMessageDecoder: MessageType.Decoder = MessageType.Decoder(moshi)

    /**
     * A conflated channel to hold the last block seen in the stream.
     *
     * From the Kotlin docs:
     *
     *  > When capacity is [KChannel.CONFLATED] — it creates a conflated channel This channel buffers at most one element
     *  and conflates all subsequent [KChannel.send] and [KChannel.trySend] invocations, so that the receiver always
     *  gets the last element sent. Back-to-back sent elements are conflated — only the last sent element is received,
     *  while previously sent elements are lost. Sending to this channel never suspends, and trySend always succeeds.
     *
     * @see [KChannel]]
     */
    //private val lastHistoricBlock: KChannel<StreamBlock> = KChannel<StreamBlock>(KChannel.CONFLATED)

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
        emitAll(streamHistoricalBlocks(startHeight = startHeight, endHeight = endHeight, internalEvents = null))
    }

    private fun streamHistoricalBlocks(
        startHeight: Long,
        internalEvents: KChannel<InternalStreamEvent>
    ): Flow<StreamBlock> = flow {
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(
            streamHistoricalBlocks(
                startHeight = startHeight,
                endHeight = endHeight,
                internalEvents = internalEvents
            )
        )
    }

    private fun streamHistoricalBlocks(
        startHeight: Long,
        endHeight: Long,
        internalEvents: KChannel<InternalStreamEvent>?
    ): Flow<StreamBlock> {

        val lastHistoricBlock = object {
            private var lastBlock: StreamBlock? = null
            private val mutex = Mutex()

            suspend fun store(block: StreamBlock): StreamBlock {
                mutex.withLock {
                    lastBlock = block
                }
                return block
            }

            fun get(): StreamBlock? = lastBlock
        }

        return flow {
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
            .withIndex()
            .map { indexed ->
                val index: Int = indexed.index
                val heightPairChunk: List<Pair<Long, Long>> =
                    indexed.value // each pair will be `TENDERMINT_MAX_QUERY_RANGE` units apart

                val lowest = heightPairChunk.minOf { it.first }
                val highest = heightPairChunk.maxOf { it.second }
                val fullBlockHeights: Set<Long> = (lowest..highest).toSet()

                // Update every 2000 blocks:
                if ((index % 20) == 0) {
                    dynamo.writeMaxHistoricalBlockHeight(highest)
                        .also {
                            if (it.processed > 0) {
                                log.info("historical::updating max historical block height to $highest")
                            }
                        }
                }

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
                        }
                        .awaitAll()
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

                    log.info("historical::${availableBlocks.size} block(s) in [$lowest..$highest]")

                    Pair(seenBlockMap, availableBlocks)
                }

                availableBlocks.map { height: Long -> Pair(height, seenBlockMap[height]) }
            }
            .flowOn(dispatchers.io())
            .flatMapMerge(options.concurrency) { queryBlocks(it) }
            .flowOn(dispatchers.io())
            .map {
                // Mark the block as historical and update the last block seen:
                val block = it.copy(historical = true)
                lastHistoricBlock.store(block)
            }
            .onCompletion { cause: Throwable? ->
                if (cause == null) {
                    log.info("historical::exhausted historical block stream ok")

                    // signal that the historical stream has terminated to the live stream if necessary:
                    internalEvents
                        ?.trySend(InternalStreamEvent.HistoricStreamCompleted)
                        ?.also { result ->
                            if (result.isSuccess) {
                                log.info("historical::signaled that stream has finished ok")
                            } else if (result.isFailure) {
                                log.warn("historical::failed to signal that stream has finished")
                            }
                        }

                    // Update the max block height if applicable:
                    lastHistoricBlock
                        .get()
                        ?.let { block ->
                            log.info("historical::last block seen: ${block.height}")
                            block.height
                        }
                        ?.let { height ->
                            dynamo.writeMaxHistoricalBlockHeight(height)
                                .also { result ->
                                    if (result.processed > 0) {
                                        log.info("historical::updated final block height to $height")
                                    }
                                }
                        }
                } else {
                    log.error("historical::exhausted block stream with error: ${cause.message}")
                }
            }
    }

    /**
     * Internal implementation of `streamLiveBlocks()`.
     */
    private fun streamLiveBlocks(internalEvents: KChannel<InternalStreamEvent>?): Flow<StreamBlock> {

        // Toggle the Lifecycle register start state:
        eventStreamService.startListening()

        val historicalStreamFinished: AtomicBoolean = AtomicBoolean(false)

        return channelFlow {

            // Listen and update based on received messages from the historical block stream:
            val eventWatcher: Job? =
                internalEvents?.let { channel ->
                    launch {
                        while (true) {
                            // this will suspend the coroutine until a message is received:
                            when (channel.receive()) {
                                InternalStreamEvent.HistoricStreamCompleted -> historicalStreamFinished.set(true)
                            }
                        }
                    }
                }

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
                                        val block = type.block.data.value.block
                                        log.info("live::received NewBlock message: #${block.header?.height}")
                                        send(block)
                                    }
                                    is MessageType.Error ->
                                        log.error("upstream error from RPC endpoint: ${type.error}")
                                    is MessageType.Panic -> {
                                        log.error("upstream panic from RPC endpoint: ${type.error}")
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

            eventWatcher?.let {
                log.info("live::event watcher closed")
                it.cancel()
            }
        }
            .flowOn(dispatchers.io())
            .onStart {
                log.info("live::starting")
            }
            .onEach { block: Block ->
                block.header?.height?.also { height ->
                    // Check if we're allowed to update the max block height now that the historical stream has finished:
                    if (historicalStreamFinished.get()) {
                        dynamo.writeMaxHistoricalBlockHeight(height)
                            .also {
                                if (it.processed > 0) {
                                    log.info("live::updating max historical block height to $height")
                                }
                            }
                    }
                }
            }
            .mapNotNull { block: Block ->
                val maybeBlock = queryBlock(Either.Right(block), skipIfNoTxs = false)
                if (maybeBlock != null) {
                    log.info("live::got block #${maybeBlock.height}")
                    maybeBlock
                } else {
                    log.info("live::skipping block #${block.header?.height}")
                    null
                }
            }
            .onCompletion {
                log.info("live::stopping event stream")
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
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    fun streamLiveBlocks(): Flow<StreamBlock> = streamLiveBlocks(internalEvents = null)

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
        // A merged live and historical stream is a special case where the two streams need to communicate between
        // each other: the live stream needs to know when the historical stream is done so it can start recording
        // max block heights. We can use a "rendezvous" channel to allow the two to communicate (no buffer, and the
        // live block stream must be receiving at the time the historical stream sends a message, which we can
        // guarantee by implementation.
        val internalEvents = KChannel<InternalStreamEvent>(KChannel.RENDEZVOUS)
        emitAll(
            if (startingHeight != null) {
                log.info("Listening for live and historical blocks from height $startingHeight")
                merge(streamHistoricalBlocks(startingHeight, internalEvents), streamLiveBlocks(internalEvents))
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

