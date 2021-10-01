package io.provenance.aggregate.service.test.utils

import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.stream.EventStream
import io.provenance.aggregate.service.stream.EventStreamService
import io.provenance.aggregate.service.stream.TendermintService
import io.provenance.aggregate.service.stream.models.ABCIInfoResponse
import io.provenance.aggregate.service.stream.models.BlockResponse
import io.provenance.aggregate.service.stream.models.BlockResultsResponse
import io.provenance.aggregate.service.stream.models.BlockchainResponse
import io.provenance.aggregate.service.test.mocks.MockAwsInterface
import io.provenance.aggregate.service.test.mocks.MockEventStreamService
import io.provenance.aggregate.service.test.mocks.MockTendermintService
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
                mapOf("last_block_height" to MAX_BLOCK_HEIGHT)
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
     * Create a mock of the Tendermint RPC event stream exposed on Provenance.
     */
    fun eventStreamService(includeLiveBlocks: Boolean = true): MockEventStreamService.Builder {
        val serviceBuilder = MockEventStreamService.builder()
        if (includeLiveBlocks) {
            for (liveBlockResponse in Defaults.templates.readAll("live")) {
                serviceBuilder.response(liveBlockResponse)
            }
        }
        return serviceBuilder
    }

    object AwsInterfaceBuilder {
        fun build(): AwsInterface = MockAwsInterface
            .Builder()
            .build(Defaults.s3Config, Defaults.dynamoConfig)
    }

    fun defaultAws(): AwsInterfaceBuilder = AwsInterfaceBuilder

    /**
     * Create a mock of the Provenance block event stream.
     */
    data class EventStreamBuilder(val builders: Builders) {
        var dispatchers: DispatcherProvider? = null
        var eventStreamService: EventStreamService? = null
        var tendermintService: TendermintService? = null
        var dynamoInterface: AwsDynamoInterface? = null
        var moshi: Moshi? = null
        var options: EventStream.Options.Builder = EventStream.Options.builder()
        var includeLiveBlocks: Boolean = true

        fun <T : EventStreamService> eventStreamService(value: T) = apply { eventStreamService = value }
        fun <T : TendermintService> tendermintService(value: T) = apply { tendermintService = value }
        fun <T : AwsDynamoInterface> dynamoInterface(value: T) = apply { dynamoInterface = value }
        fun moshi(value: Moshi) = apply { moshi = value }
        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }
        fun options(value: EventStream.Options.Builder) = apply { options = value }
        fun includeLiveBlocks(value: Boolean) = apply { includeLiveBlocks = value }

        // shortcuts for options:
        fun batchSize(value: Int) = apply { options.batchSize(value) }
        fun fromHeight(value: Long) = apply { options.fromHeight(value) }
        fun toHeight(value: Long) = apply { options.toHeight(value) }
        fun skipIfEmpty(value: Boolean) = apply { options.skipIfEmpty(value) }
        fun skipIfSeen(value: Boolean) = apply { options.skipIfSeen(value) }
        fun matchBlockEvent(predicate: (event: String) -> Boolean) = apply { options.matchBlockEvent(predicate) }
        fun matchTxEvent(predicate: (event: String) -> Boolean) = apply { options.matchTxEvent(predicate) }

        suspend fun build(): EventStream {
            val dispatchers = dispatchers ?: error("dispatchers must be provided")
            return EventStream(
                eventStreamService = eventStreamService
                    ?: builders
                        .eventStreamService(includeLiveBlocks = includeLiveBlocks)
                        .dispatchers(dispatchers)
                        .build(),
                tendermintService = tendermintService
                    ?: builders.tendermintService().build(MockTendermintService::class.java),
                dynamo = dynamoInterface ?: defaultAws().build().dynamo(),
                moshi = moshi ?: Defaults.moshi,
                dispatchers = dispatchers,
                options = options.build()
            )
        }
    }

    fun eventStream(): EventStreamBuilder = EventStreamBuilder(this)
}