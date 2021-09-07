package io.provenance.aggregate.service

import arrow.core.Either
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.aggregate.service.stream.*
import io.provenance.aggregate.service.stream.json.JSONObjectAdapter
import io.provenance.aggregate.service.mocks.MockEventStreamService
import io.provenance.aggregate.service.mocks.MockTendermintService
import io.provenance.aggregate.service.mocks.ServiceMocker
import io.provenance.aggregate.service.stream.models.ABCIInfoResponse
import io.provenance.aggregate.service.stream.models.BlockResponse
import io.provenance.aggregate.service.stream.models.BlockResultsResponse
import io.provenance.aggregate.service.stream.models.BlockchainResponse
import io.provenance.aggregate.service.utils.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamTests {

    val MIN_BLOCK_HEIGHT: Long = 2270370
    val MAX_BLOCK_HEIGHT: Long = 2270469
    val EXPECTED_NONEMPTY_BLOCKS: Int = 29

    val heights: List<Long> = (MIN_BLOCK_HEIGHT..MAX_BLOCK_HEIGHT).toList()

    val heightChunks: List<Pair<Long, Long>> = heights
        .chunked(EventStream.TENDERMINT_MAX_QUERY_RANGE)
        .map { Pair(it.minOrNull()!!, it.maxOrNull()!!) }

    lateinit var moshi: Moshi
    lateinit var templates: Template
    lateinit var blockResponses: Array<BlockResponse>
    lateinit var blockResultsResponses: Array<BlockResultsResponse>
    lateinit var blockchainResponses: Array<BlockchainResponse>
    lateinit var defaultTendermintServiceBuilder: ServiceMocker.Builder

    @BeforeAll
    fun setUpFixtures() {
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(JSONObjectAdapter())
            .build()

        templates = Template(moshi)

        blockResponses =
            heights
                .map { templates.unsafeReadAs(BlockResponse::class.java, "block/${it}.json") }
                .toTypedArray()

        blockResultsResponses =
            heights
                .map { templates.unsafeReadAs(BlockResultsResponse::class.java, "block_results/${it}.json") }
                .toTypedArray()

        blockchainResponses =
            heightChunks
                .map { (minHeight, maxHeight) ->
                    templates.unsafeReadAs(
                        BlockchainResponse::class.java,
                        "blockchain/${minHeight}-${maxHeight}.json"
                    )
                }
                .toTypedArray()

        defaultTendermintServiceBuilder = ServiceMocker.Builder()
            .doFor("abciInfo") {
                templates.readAs(
                    ABCIInfoResponse::class.java,
                    "abci_info/success.json",
                    mapOf("last_block_height" to MAX_BLOCK_HEIGHT)
                )
            }
            .doFor("block") { templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
            .doFor("blockResults") { templates.readAs(BlockResultsResponse::class.java, "block_results/${it[0]}.json") }
            .doFor("blockchain") {
                templates.readAs(
                    BlockchainResponse::class.java,
                    "blockchain/${it[0]}-${it[1]}.json"
                )
            }
    }

    @Test
    fun testAbciInfoSerialization() {
        assert(templates.readAs(ABCIInfoResponse::class.java, "abci_info/success.json") != null)
    }

    @Test
    fun testAbciInfoAPIResponse() {
        val expectBlockHeight = 9999L

        val tm = ServiceMocker.Builder()
            .doFor("abciInfo") {
                templates.readAs(
                    ABCIInfoResponse::class.java,
                    "abci_info/success.json",
                    mapOf("last_block_height" to expectBlockHeight)
                )
            }
            .build(MockTendermintService::class.java)

        runBlocking {
            assert(tm.abciInfo().result?.response?.lastBlockHeight == expectBlockHeight)
        }
    }

    @Test
    fun testBlockResponse() {
        val tm = ServiceMocker.Builder()
            .doFor("block") { templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
            .build(MockTendermintService::class.java)

        val expectedHeight = MIN_BLOCK_HEIGHT

        runBlocking {
            assert(tm.block(expectedHeight).result?.block?.header?.height == expectedHeight)
        }

        assertThrows<Throwable> {
            runBlocking {
                tm.block(-1)
            }
        }

        assertThrows<Throwable> {
            runBlocking {
                tm.block(999999999)
            }
        }
    }

    @Test
    fun testBlockResultsResponse() {
        val tm = ServiceMocker.Builder()
            .doFor("blockResults") { templates.readAs(BlockResultsResponse::class.java, "block_results/${it[0]}.json") }
            .build(MockTendermintService::class.java)

        val expectedHeight = MIN_BLOCK_HEIGHT

        runBlocking {
            assert(tm.blockResults(expectedHeight).result.height == expectedHeight)
        }

        assertThrows<Throwable> {
            runBlocking {
                tm.blockResults(-1)
            }
        }

        assertThrows<Throwable> {
            runBlocking {
                tm.blockResults(999999999)
            }
        }
    }

    @Test
    fun testBlockchainResponse() {
        val tm = ServiceMocker.Builder()
            .doFor("blockchain") {
                templates.readAs(
                    BlockchainResponse::class.java,
                    "blockchain/${it[0]}-${it[1]}.json"
                )
            }
            .build(MockTendermintService::class.java)

        val expectedMinHeight: Long = MIN_BLOCK_HEIGHT
        val expectedMaxHeight: Long = expectedMinHeight + 20 - 1

        runBlocking {
            val blockMetas = tm.blockchain(expectedMinHeight, expectedMaxHeight).result?.blockMetas
            assert(blockMetas?.size == 20)
            val expectedHeights: Set<Long> = (expectedMinHeight..expectedMaxHeight).toSet()
            val heights: Set<Long> = blockMetas?.map { b -> b.header?.height }?.filterNotNull()?.toSet() ?: setOf()
            assert((expectedHeights - heights).isEmpty())
        }

        assertThrows<Throwable> {
            runBlocking {
                tm.blockchain(-expectedMinHeight, expectedMaxHeight)
            }
        }
    }

    @Test
    fun testWebsocketReceiver() {
        val heights: Set<Long> = setOf(
            MIN_BLOCK_HEIGHT,
            MIN_BLOCK_HEIGHT + 1,
            MIN_BLOCK_HEIGHT + 2
        )
        val blocks: Array<BlockResponse> =
            heights
                .map { templates.unsafeReadAs(BlockResponse::class.java, "block/${it}.json") }
                .toTypedArray()

        // set up:
        val ess: MockEventStreamService = runBlocking {
            MockEventStreamService.Builder(moshi)
                .addResponse(BlockResponse::class.java, *blocks)
                .build()
        }

        val receiver = ess.observeWebSocketEvent()

        // make sure we get the same stuff back out:
        runBlocking {
            val event = receiver.receive()  // block
            assert(event is WebSocket.Event.OnMessageReceived)
            val payload = ((event as WebSocket.Event.OnMessageReceived).message as Message.Text).value
            val response: BlockResponse? = moshi.adapter(BlockResponse::class.java).fromJson(payload)
            assert(response?.result?.block?.header?.height in heights)
        }
    }

    @Test
    fun testHistoricalBlockStreaming() {

        val eventStreamService: MockEventStreamService = runBlocking {
            MockEventStreamService.Builder(moshi).build()
        }

        val tendermintService = defaultTendermintServiceBuilder
            .build(MockTendermintService::class.java)

        // If skipping empty blocks, we should get 100:
        val eventStreamNoSkip = EventStream(
            eventStreamService,
            tendermintService,
            moshi,
            batchSize = 4,
            skipIfEmpty = false
        )
        runBlocking(Dispatchers.Default) {
            val events: Flow<Either<Throwable, StreamBlock>> =
                eventStreamNoSkip.streamHistoricalBlocks(MIN_BLOCK_HEIGHT)
            val collected = events.toList()
            assert(collected.size == 100)
            assert(collected.all { it.isRight() && it.orNull()?.historical ?: false })
        }

        // If skipping empty blocks, we should get 100:
        val eventStreamSkip = EventStream(
            eventStreamService,
            tendermintService,
            moshi,
            batchSize = 4,
            skipIfEmpty = true
        )

        runBlocking(Dispatchers.Default) {
            val events: Flow<Either<Throwable, StreamBlock>> = eventStreamSkip.streamHistoricalBlocks(MIN_BLOCK_HEIGHT)
            val collected = events.toList()
            assert(collected.size == EXPECTED_NONEMPTY_BLOCKS)
            assert(collected.all { it.isRight() && it.orNull()?.historical ?: false })
        }
    }

    @Test
    fun testLiveBlockStreaming() {
        val eventStreamService: MockEventStreamService = runBlocking {
            val serviceBuilder = MockEventStreamService.Builder(moshi)
            for (liveBlockResponse in templates.readAll("live")) {
                serviceBuilder.addResponse(liveBlockResponse)
            }
            serviceBuilder.build()
        }

        val tendermintService = defaultTendermintServiceBuilder
            .build(MockTendermintService::class.java)

        val eventStream = EventStream(
            eventStreamService,
            tendermintService,
            moshi,
            batchSize = 4,
            skipIfEmpty = false
        )

        runBlocking(Dispatchers.Default) {
            // Explicitly taking the expected response count items from the io.provenance.aggregate.service.flow will implicitly cancel it after
            // that number of items as been consumed. Otherwise, the mock websocket receiver (like the real one),
            // will just wait indefinitely for further events.
            val collected: List<Either<Throwable, StreamBlock>> = eventStream.streamLiveBlocks()
                .take(eventStreamService.expectedResponseCount())
                .toList()
            // All blocks should be marked as "live":
            assert(collected.all { it.isRight() && !(it.orNull()?.historical ?: true) })
        }
    }

    @Test
    fun testCombinedBlockStreaming() {
        val eventStreamService: MockEventStreamService = runBlocking {
            val serviceBuilder = MockEventStreamService.Builder(moshi)
            for (liveBlockResponse in templates.readAll("live")) {
                serviceBuilder.addResponse(liveBlockResponse)
            }
            serviceBuilder.build()
        }

        val tendermintService = defaultTendermintServiceBuilder
            .build(MockTendermintService::class.java)

        val eventStream = EventStream(
            eventStreamService,
            tendermintService,
            moshi,
            batchSize = 4,
            skipIfEmpty = true
        )

        runBlocking(Dispatchers.Default) {
            val expectTotal: Int = EXPECTED_NONEMPTY_BLOCKS + eventStreamService.expectedResponseCount()
            val collected = eventStream.streamBlocks(MIN_BLOCK_HEIGHT)
                .take(expectTotal)
                .toList()
            assert(collected.size == expectTotal)
            assert(collected.filter { it.orNull()!!.historical }.size == EXPECTED_NONEMPTY_BLOCKS)
            assert(collected.filter { !it.orNull()!!.historical }.size == eventStreamService.expectedResponseCount())
        }
    }
}
