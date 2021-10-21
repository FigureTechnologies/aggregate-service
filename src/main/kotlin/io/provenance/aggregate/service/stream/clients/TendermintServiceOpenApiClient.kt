package io.provenance.aggregate.service.stream.clients

import io.provenance.aggregate.service.stream.TendermintServiceClient
import io.provenance.aggregate.common.apis.ABCIApi
import io.provenance.aggregate.common.apis.InfoApi
import io.provenance.aggregate.common.models.ABCIInfoResponse
import io.provenance.aggregate.common.models.BlockResponse
import io.provenance.aggregate.common.models.BlockResultsResponse
import io.provenance.aggregate.common.models.BlockchainResponse

/**
 * An OpenAPI generated client designed to interact with the Tendermint RPC API.
 *
 * All requests and responses are HTTP+JSON.
 *
 * @property rpcUrlBase The base URL of the Tendermint RPC API to use when making requests.
 */
class TendermintServiceOpenApiClient(rpcUrlBase: String) : TendermintServiceClient {
    private val abciApi = ABCIApi(rpcUrlBase)
    private val infoApi = InfoApi(rpcUrlBase)

    override suspend fun abciInfo(): ABCIInfoResponse = abciApi.abciInfo()

    override suspend fun block(height: Long?): BlockResponse = infoApi.block(height)

    override suspend fun blockResults(height: Long?): BlockResultsResponse = infoApi.blockResults(height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)
}
