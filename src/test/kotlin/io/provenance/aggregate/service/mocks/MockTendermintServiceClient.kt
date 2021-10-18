package io.provenance.aggregate.service.test.mocks

import io.provenance.aggregate.service.stream.TendermintServiceClient
import io.provenance.aggregate.service.stream.models.*

class MockTendermintServiceClient(mocker: ServiceMock) : TendermintServiceClient, ServiceMock by mocker {

    override suspend fun abciInfo() =
        respondWith<ABCIInfoResponse>("abciInfo")

    override suspend fun block(height: Long?) =
        respondWith<BlockResponse>("block", height)

    override suspend fun blockResults(height: Long?) =
        respondWith<BlockResultsResponse>("blockResults", height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?) =
        respondWith<BlockchainResponse>("blockchain", minHeight, maxHeight)
}