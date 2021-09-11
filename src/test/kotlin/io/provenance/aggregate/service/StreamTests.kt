package io.provenance.aggregate.service

import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.aggregate.service.base.TestBase
import io.provenance.aggregate.service.mocks.MockEventStreamService
import io.provenance.aggregate.service.mocks.MockTendermintService
import io.provenance.aggregate.service.mocks.NoOpDynamo
import io.provenance.aggregate.service.mocks.ServiceMocker
import io.provenance.aggregate.service.stream.TendermintService
import io.provenance.aggregate.service.stream.models.ABCIInfoResponse
import io.provenance.aggregate.service.stream.models.BlockResponse
import io.provenance.aggregate.service.stream.models.BlockResultsResponse
import io.provenance.aggregate.service.stream.models.BlockchainResponse
import io.provenance.aggregate.service.utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamTests : TestBase() {

    // Fake Dynamo usage for these tests. This implementation stores nothing and returns nothing when fetching
    // block metadata by block height:
    val noopDynamo = NoOpDynamo()

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testAbciInfoSerialization() {
        assert(templates.readAs(ABCIInfoResponse::class.java, "abci_info/success.json") != null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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

        dispatcherProvider.runBlockingTest {
            assert(tm.abciInfo().result?.response?.lastBlockHeight == expectBlockHeight)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testBlockResponse() {

        val tendermint: TendermintService = ServiceMocker.Builder()
            .doFor("block") { templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
            .build(MockTendermintService::class.java)

        val expectedHeight = MIN_BLOCK_HEIGHT

        // Expect success:
        dispatcherProvider.runBlockingTest {
            assert(tendermint.block(expectedHeight).result?.block?.header?.height == expectedHeight)
        }

        // Retrieving a non-existent block should fail (negative case):
        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.block(-1)
            }
        }

        // Retrieving a non-existent block should fail (non-existent case):
        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.block(999999999)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testBlockResultsResponse() {

        val tendermint: TendermintService = ServiceMocker.Builder()
            .doFor("blockResults") { templates.readAs(BlockResultsResponse::class.java, "block_results/${it[0]}.json") }
            .build(MockTendermintService::class.java)

        val expectedHeight = MIN_BLOCK_HEIGHT

        // Expect success:
        dispatcherProvider.runBlockingTest {
            assert(tendermint.blockResults(expectedHeight).result.height == expectedHeight)
        }

        // Retrieving a non-existent block should fail (negative case):
        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.blockResults(-1)
            }
        }

        // Retrieving a non-existent block should fail (non-existent case):
        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.blockResults(999999999)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testBlockchainResponse() {

        val tendermint: TendermintService = ServiceMocker.Builder()
            .doFor("blockchain") {
                templates.readAs(
                    BlockchainResponse::class.java,
                    "blockchain/${it[0]}-${it[1]}.json"
                )
            }
            .build(MockTendermintService::class.java)

        val expectedMinHeight: Long = MIN_BLOCK_HEIGHT
        val expectedMaxHeight: Long = expectedMinHeight + 20 - 1

        dispatcherProvider.runBlockingTest {
            val blockMetas = tendermint.blockchain(expectedMinHeight, expectedMaxHeight).result?.blockMetas

            assert(blockMetas?.size == 20)

            val expectedHeights: Set<Long> = (expectedMinHeight..expectedMaxHeight).toSet()
            val heights: Set<Long> = blockMetas?.mapNotNull { b -> b.header?.height }?.toSet() ?: setOf()

            assert((expectedHeights - heights).isEmpty())

        }

        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.blockchain(-expectedMinHeight, expectedMaxHeight)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWebsocketReceiver() {

        dispatcherProvider.runBlockingTest {
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
            val ess = MockEventStreamService
                .builder()
                .dispatchers(dispatcherProvider)
                .response(BlockResponse::class.java, *blocks)
                .build()

            val receiver = ess.observeWebSocketEvent()

            // make sure we get the same stuff back out:
            dispatcherProvider.runBlockingTest {
                val event = receiver.receive()  // block
                assert(event is WebSocket.Event.OnMessageReceived)
                val payload = ((event as WebSocket.Event.OnMessageReceived).message as Message.Text).value
                val response: BlockResponse? = moshi.adapter(BlockResponse::class.java).fromJson(payload)
                assert(response?.result?.block?.header?.height in heights)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    @Test
    fun testHistoricalBlockStreaming() {

        dispatcherProvider.runBlockingTest {

            // If not skipping empty blocks, we should get 100:
            val collectedNoSkip = Builders.eventStream()
                .dispatchers(dispatcherProvider)
                .dynamoInterface(noopDynamo)
                .fromHeight(MIN_BLOCK_HEIGHT)
                .skipIfEmpty(false)
                .build()
                .streamHistoricalBlocks()
                .toList()

            assert(collectedNoSkip.size.toLong() == EXPECTED_TOTAL_BLOCKS)
            assert(collectedNoSkip.all { it.isRight() && it.orNull()?.historical ?: false })

            // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
            val collectedSkip = Builders.eventStream()
                .dispatchers(dispatcherProvider)
                .dynamoInterface(noopDynamo)
                .fromHeight(MIN_BLOCK_HEIGHT)
                .build()
                .streamHistoricalBlocks().toList()
                .toList()

            assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
            assert(collectedSkip.all { it.isRight() && it.orNull()?.historical ?: false })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testLiveBlockStreaming() {

        dispatcherProvider.runBlockingTest {

            val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                .dispatchers(dispatcherProvider)
                .build()

            val tendermintService = Builders.tendermintService()
                .build(MockTendermintService::class.java)

            val eventStream = Builders.eventStream()
                .dispatchers(dispatcherProvider)
                .eventStreamService(eventStreamService)
                .tendermintService(tendermintService)
                .dynamoInterface(noopDynamo)
                .skipIfEmpty(false)
                .build()

            val collected = eventStream
                .streamLiveBlocks()
                .toList()

            assert(eventStreamService.expectedResponseCount() > 0)
            assert(collected.size == eventStreamService.expectedResponseCount().toInt())

            // All blocks should be marked as "live":
            assert(collected.all { it.isRight() && !(it.orNull()?.historical ?: true) })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    @Test
    fun testCombinedBlockStreaming() {

        dispatcherProvider.runBlockingTest {

            val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                .dispatchers(dispatcherProvider)
                .build()

            val tendermintService = Builders.tendermintService()
                .build(MockTendermintService::class.java)

            val eventStream = Builders.eventStream()
                .dispatchers(dispatcherProvider)
                .eventStreamService(eventStreamService)
                .tendermintService(tendermintService)
                .dynamoInterface(noopDynamo)
                .fromHeight(MIN_BLOCK_HEIGHT)
                .skipIfEmpty(true)
                .build()

            val expectTotal = EXPECTED_NONEMPTY_BLOCKS + eventStreamService.expectedResponseCount()

            val collected = eventStream
                .streamBlocks()
                .toList()

            assert(collected.size == expectTotal.toInt())
            assert(collected.filter { it.orNull()!!.historical }.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
            assert(collected.filter { !it.orNull()!!.historical }.size.toLong() == eventStreamService.expectedResponseCount())
        }
    }
}
