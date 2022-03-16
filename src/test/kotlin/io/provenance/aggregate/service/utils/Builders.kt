package io.provenance.aggregate.service.test.utils

import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.common.aws.AwsClient
import io.provenance.aggregate.common.aws.dynamodb.client.DynamoClient
import io.provenance.aggregate.service.stream.EventStreamLegacy
import io.provenance.aggregate.service.stream.EventStreamServiceLegacy
import io.provenance.aggregate.service.stream.TendermintServiceClient
import io.provenance.aggregate.common.models.ABCIInfoResponse
import io.provenance.aggregate.common.models.BlockResponse
import io.provenance.aggregate.common.models.BlockResultsResponse
import io.provenance.aggregate.common.models.BlockchainResponse
import io.provenance.aggregate.service.test.mocks.MockAwsClient
import io.provenance.aggregate.service.test.mocks.MockEventStreamLegacyService
import io.provenance.aggregate.service.test.mocks.MockTendermintServiceClient
import io.provenance.aggregate.service.test.mocks.ServiceMocker
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
object Builders {

    /**
     * Create a mock of the Tendermint service API exposed on Provenance.
     */
    fun tendermintService(): ServiceMocker.Builder = ServiceMocker.Builder()
        .doFor("abciInfo") {
            Defaults.templates.readAs(
                ABCIInfoResponse::class.java,
                "abci_info/success.json",
                mapOf("last_block_height" to MAX_HISTORICAL_BLOCK_HEIGHT)
            )
        }
        .doFor("block") { Defaults.templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
        .doFor("blockResults") {
            Defaults.templates.readAs(
                BlockResultsResponse::class.java,
                "block_results/${it[0]}.json"
            )
        }
        .doFor("blockchain") {
            Defaults.templates.readAs(
                BlockchainResponse::class.java,
                "blockchain/${it[0]}-${it[1]}.json"
            )
        }

    /**
     * Create a mock of the Tendermint service API exposed on Provenance with Custom response.
     */
    fun tendermintServiceCustom(customJson: String): ServiceMocker.Builder = ServiceMocker.Builder()
        .doFor("abciInfo") {
            Defaults.templates.readAs(
                ABCIInfoResponse::class.java,
                "abci_info/success.json",
                mapOf("last_block_height" to MAX_HISTORICAL_BLOCK_HEIGHT)
            )
        }
        .doFor("block") { Defaults.templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
        .doFor("blockResults") {
            Defaults.templates.readAs(
                BlockResultsResponse::class.java,
                "block_results/${customJson}"
            )
        }
        .doFor("blockchain") {
            Defaults.templates.readAs(
                BlockchainResponse::class.java,
                "blockchain/${customJson}"
            )
        }

    /**
     * Create a mock of the Tendermint RPC event stream exposed on Provenance.
     */
    fun eventStreamService(includeLiveBlocks: Boolean = true): MockEventStreamLegacyService.Builder {
        val serviceBuilder = MockEventStreamLegacyService.builder()
        if (includeLiveBlocks) {
            for (liveBlockResponse in Defaults.templates.readAll("live")) {
                serviceBuilder.response(liveBlockResponse)
            }
        }
        return serviceBuilder
    }

    object AwsInterfaceBuilder {
        fun build(): AwsClient = MockAwsClient
            .Builder()
            .build(Defaults.s3Config, Defaults.dynamoConfig)
    }

    fun defaultAws(): AwsInterfaceBuilder = AwsInterfaceBuilder

    /**
     * Create a mock of the Provenance block event stream.
     */
    data class EventStreamBuilder(val builders: Builders) {
        var dispatchers: DispatcherProvider? = null
        var eventStreamService: EventStreamServiceLegacy? = null
        var tendermintServiceClient: TendermintServiceClient? = null
        var dynamoInterface: DynamoClient? = null
        var moshi: Moshi? = null
        var options: EventStreamLegacy.Options.Builder = EventStreamLegacy.Options.builder()
        var includeLiveBlocks: Boolean = true
        var feeCollector: String = ""
        var dynamoBatchGetItems: Long = 100

        fun <T : EventStreamServiceLegacy> eventStreamService(value: T) = apply { eventStreamService = value }
        fun <T : TendermintServiceClient> tendermintService(value: T) = apply { tendermintServiceClient = value }
        fun <T : DynamoClient> dynamoInterface(value: T) = apply { dynamoInterface = value }
        fun moshi(value: Moshi) = apply { moshi = value }
        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }
        fun options(value: EventStreamLegacy.Options.Builder) = apply { options = value }
        fun includeLiveBlocks(value: Boolean) = apply { includeLiveBlocks = value }
        fun feeCollector(value: String) = apply { feeCollector = value }
        fun dynamoBatchGetItems(value: Long) = apply{ dynamoBatchGetItems = value }

        // shortcuts for options:
        fun batchSize(value: Int) = apply { options.batchSize(value) }
        fun fromHeight(value: Long) = apply { options.fromHeight(value) }
        fun toHeight(value: Long) = apply { options.toHeight(value) }
        fun skipIfEmpty(value: Boolean) = apply { options.skipIfEmpty(value) }
        fun skipIfSeen(value: Boolean) = apply { options.skipIfSeen(value) }
        fun matchBlockEvent(predicate: (event: String) -> Boolean) = apply { options.matchBlockEvent(predicate) }
        fun matchTxEvent(predicate: (event: String) -> Boolean) = apply { options.matchTxEvent(predicate) }

        suspend fun build(): EventStreamLegacy {
            val dispatchers = dispatchers ?: error("dispatchers must be provided")
            return EventStreamLegacy(
                eventStreamService = eventStreamService
                    ?: builders
                        .eventStreamService(includeLiveBlocks = includeLiveBlocks)
                        .dispatchers(dispatchers)
                        .build(),
                tendermintServiceClient = tendermintServiceClient
                    ?: builders.tendermintService().build(MockTendermintServiceClient::class.java),
                dynamo = dynamoInterface ?: defaultAws().build().dynamo(),
                moshi = moshi ?: Defaults.moshi,
                dispatchers = dispatchers,
                feeCollector = feeCollector,
                dynamoBatchGetItems = dynamoBatchGetItems,
                options = options.build()
            )
        }
    }

    fun eventStream(): EventStreamBuilder = EventStreamBuilder(this)
}
