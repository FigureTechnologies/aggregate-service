package io.provenance.aggregate.service

import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.aggregate.service.aws.dynamodb.NoOpDynamo
import io.provenance.aggregate.service.base.TestBase
import io.provenance.aggregate.service.mocks.MockEventStreamService
import io.provenance.aggregate.service.mocks.MockTendermintService
import io.provenance.aggregate.service.mocks.ServiceMocker
import io.provenance.aggregate.service.stream.TendermintService
import io.provenance.aggregate.service.stream.models.*
import io.provenance.aggregate.service.stream.models.extensions.toDecodedMap
import io.provenance.aggregate.service.utils.Builders
import io.provenance.aggregate.service.utils.EXPECTED_NONEMPTY_BLOCKS
import io.provenance.aggregate.service.utils.EXPECTED_TOTAL_BLOCKS
import io.provenance.aggregate.service.utils.MIN_BLOCK_HEIGHT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
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

    @Nested
    inner class EventAttributes {

        @Test
        fun testDecodingIntoMap() {
            val recordAddrValue =
                "InJlY29yZDFxMm0zeGFneDc2dXl2ZzRrN3l2eGM3dWhudWdnOWc2bjBsY2Robm43YXM2YWQ4a3U4Z3ZmdXVnZjZ0aiI="
            val sessionAddrValue =
                "InNlc3Npb24xcXhtM3hhZ3g3NnV5dmc0azd5dnhjN3VobnVnMHpwdjl1cTNhdTMzMmsyNzY2NmplMGFxZ2o4Mmt3dWUi"
            val scopeAddrValue = "InNjb3BlMXF6bTN4YWd4NzZ1eXZnNGs3eXZ4Yzd1aG51Z3F6ZW1tbTci"
            val eventAttributes: List<Event> = listOf(
                Event(key = "cmVjb3JkX2FkZHI=", value = recordAddrValue, index = false),
                Event(key = "c2Vzc2lvbl9hZGRy", value = sessionAddrValue, index = false),
                Event(key = "c2NvcGVfYWRkcg==", value = scopeAddrValue, index = false)
            )
            val attributeMap = eventAttributes.toDecodedMap()
            assert("record_addr" in attributeMap && attributeMap["record_addr"] == recordAddrValue)
            assert("session_addr" in attributeMap && attributeMap["session_addr"] == sessionAddrValue)
            assert("scope_addr" in attributeMap && attributeMap["scope_addr"] == scopeAddrValue)
        }
    }

    @Nested
    inner class TendermintAPI {

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
                .doFor("blockResults") {
                    templates.readAs(
                        BlockResultsResponse::class.java,
                        "block_results/${it[0]}.json"
                    )
                }
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
    }

    @Nested
    inner class Streaming {

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
                assert(collectedNoSkip.all { it.historical ?: false })

                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .dynamoInterface(noopDynamo)
                    .fromHeight(MIN_BLOCK_HEIGHT)
                    .build()
                    .streamHistoricalBlocks().toList()
                    .toList()

                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
                assert(collectedSkip.all { it.historical ?: false })
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
                assert(collected.all { !(it.historical ?: true) })
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
                assert(collected.filter { it.historical }.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
                assert(collected.filter { !it.historical }.size.toLong() == eventStreamService.expectedResponseCount())
            }
        }
    }

    @Nested
    inner class StreamFiltering {
        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testTxEventFilteringByEventIfNotMatching() {
            dispatcherProvider.runBlockingTest {

                val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                    .dispatchers(dispatcherProvider)
                    .build()

                val tendermintService = Builders.tendermintService()
                    .build(MockTendermintService::class.java)

                val requireTxEvent = "DOES-NOT-EXIST"

                val eventStream = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .eventStreamService(eventStreamService)
                    .tendermintService(tendermintService)
                    .dynamoInterface(noopDynamo)
                    .fromHeight(MIN_BLOCK_HEIGHT)
                    .skipIfEmpty(true)
                    .matchTxEvent { it == requireTxEvent }
                    .build()

                assert(eventStream.streamBlocks().count() == 0)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testTxEventFilteringByEventIfMatching() {
            dispatcherProvider.runBlockingTest {

                val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                    .dispatchers(dispatcherProvider)
                    .build()

                val tendermintService = Builders.tendermintService()
                    .build(MockTendermintService::class.java)

                val requireTxEvent = "provenance.metadata.v1.EventRecordCreated"

                val eventStream = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .eventStreamService(eventStreamService)
                    .tendermintService(tendermintService)
                    .dynamoInterface(noopDynamo)
                    .fromHeight(MIN_BLOCK_HEIGHT)
                    .skipIfEmpty(true)
                    .matchTxEvent { it == requireTxEvent }
                    .build()

                val collected = eventStream
                    .streamBlocks()
                    .toList()

                assert(collected.all {
                    requireTxEvent in it.txEvents.map { e -> e.eventType }
                })
            }
        }
    }
}
