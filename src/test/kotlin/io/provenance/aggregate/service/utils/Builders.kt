package io.provenance.aggregate.service.utils

import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.mocks.MockEventStreamService
import io.provenance.aggregate.service.mocks.MockTendermintService
import io.provenance.aggregate.service.mocks.ServiceMocker
import io.provenance.aggregate.service.stream.EventStream
import io.provenance.aggregate.service.stream.EventStreamService
import io.provenance.aggregate.service.stream.TendermintService
import io.provenance.aggregate.service.stream.models.ABCIInfoResponse
import io.provenance.aggregate.service.stream.models.BlockResponse
import io.provenance.aggregate.service.stream.models.BlockResultsResponse
import io.provenance.aggregate.service.stream.models.BlockchainResponse

object Builders {

    fun defaultTendermintService(): ServiceMocker.Builder = ServiceMocker.Builder()
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

    suspend fun defaultEventStreamService(includeLiveBlocks: Boolean = true): MockEventStreamService.Builder {
        val serviceBuilder = MockEventStreamService.Builder(Defaults.moshi)
        if (includeLiveBlocks) {
            for (liveBlockResponse in Defaults.templates.readAll("live")) {
                serviceBuilder.addResponse(liveBlockResponse)
            }
        }
        return serviceBuilder
    }

    suspend fun <T : TendermintService> defaultEventStream(
        dispatchers: DispatcherProvider,
        tendermintServiceClass: Class<T>,
        includeLiveBlocks: Boolean = true,
        batchSize: Int = BATCH_SIZE,
        skipIfEmpty: Boolean = true,
    ): EventStream =
        EventStream(
            defaultEventStreamService(includeLiveBlocks = includeLiveBlocks).build(),
            defaultTendermintService().build(tendermintServiceClass),
            Defaults.moshi,
            dispatchers = dispatchers,
            batchSize = batchSize,
            skipIfEmpty = skipIfEmpty
        )

    suspend fun defaultEventStream(
        dispatchers: DispatcherProvider,
        includeLiveBlocks: Boolean = true,
        batchSize: Int = BATCH_SIZE,
        skipIfEmpty: Boolean = true,
    ): EventStream = defaultEventStream(
        dispatchers,
        MockTendermintService::class.java,
        includeLiveBlocks = includeLiveBlocks,
        batchSize = batchSize,
        skipIfEmpty = skipIfEmpty
    )

    fun <E : EventStreamService, T : TendermintService> defaultEventStream(
        dispatchers: DispatcherProvider,
        eventStreamService: E,
        tendermintService: T,
        batchSize: Int = BATCH_SIZE,
        skipIfEmpty: Boolean = true,
    ): EventStream =
        EventStream(
            eventStreamService,
            tendermintService,
            Defaults.moshi,
            dispatchers = dispatchers,
            batchSize = batchSize,
            skipIfEmpty = skipIfEmpty
        )
}