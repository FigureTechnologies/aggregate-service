package io.provenance.aggregate.service.stream

import io.provenance.aggregate.common.models.ABCIInfoResponse
import io.provenance.aggregate.common.models.BlockResponse
import io.provenance.aggregate.common.models.BlockResultsResponse
import io.provenance.aggregate.common.models.BlockchainResponse

/**
 * A client designed to interact with the Tendermint RPC API.
 */
interface TendermintServiceClient {
    suspend fun abciInfo(): ABCIInfoResponse
    suspend fun block(height: Long?): BlockResponse
    suspend fun blockResults(height: Long?): BlockResultsResponse
    suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse
}

