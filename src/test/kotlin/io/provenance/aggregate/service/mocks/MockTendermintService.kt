package io.provenance.aggregate.service.mocks

import io.provenance.aggregate.service.mocks.ServiceMock
import io.provenance.aggregate.service.stream.TendermintService
import io.provenance.aggregate.service.stream.models.*

class MockTendermintService(mocker: ServiceMock) : TendermintService, ServiceMock by mocker {

    override suspend fun abciInfo() =
        respondWith<ABCIInfoResponse>("abciInfo")

    override suspend fun block(height: Long?) =
        respondWith<BlockResponse>("block", height)

    override suspend fun blockResults(height: Long?) =
        respondWith<BlockResultsResponse>("blockResults", height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?) =
        respondWith<BlockchainResponse>("blockchain", minHeight, maxHeight)
}