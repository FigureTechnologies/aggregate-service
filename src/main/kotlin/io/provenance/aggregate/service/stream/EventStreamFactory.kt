package io.provenance.aggregate.service.stream

import io.provenance.aggregate.service.stream.apis.ABCIApi
import io.provenance.aggregate.service.stream.apis.InfoApi
import io.provenance.aggregate.service.stream.models.ABCIInfoResponse
import io.provenance.aggregate.service.stream.models.BlockResponse
import io.provenance.aggregate.service.stream.models.BlockResultsResponse
import io.provenance.aggregate.service.stream.models.BlockchainResponse
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.service.Config

class ABCIApiClientImpl(rpcBaseUrl: String) : ABCIApiClient {
    val abciApi: ABCIApi = ABCIApi(rpcBaseUrl)
    override suspend fun abciInfo(): ABCIInfoResponse = abciApi.abciInfo()
}

class InfoApiClientImpl(rpcBaseUrl: String) : InfoApiClient {
    val infoApi: InfoApi = InfoApi(rpcBaseUrl)
    override suspend fun block(height: Long?): BlockResponse = infoApi.block(height)
    override suspend fun blockResults(height: Long?): BlockResultsResponse = infoApi.blockResults(height)
    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)
}

class EventStreamFactory(
    private val config: Config,
    private val eventStreamBuilder: Scarlet.Builder
) {
    fun getStream(skipEmptyBlocks: Boolean = true): EventStream {
        val lifecycle = LifecycleRegistry(0L)
        val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
        val eventStreamService: EventStreamService = scarlet.create()
        val abciApi = ABCIApiClientImpl(config.event.stream.rpc_uri)
        val infoApi = InfoApiClientImpl(config.event.stream.rpc_uri)

        return EventStream(
            lifecycle,
            eventStreamService,
            abciApi,
            infoApi,
            batchSize = config.event.stream.batch_size,
            skipIfEmpty = skipEmptyBlocks
        )
    }
}
