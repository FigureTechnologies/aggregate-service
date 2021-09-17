package io.provenance.aggregate.service.mocks

import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.service.aws.dynamodb.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoOpDynamo : AwsDynamoInterface {

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? = null

    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> =
        emptyFlow<BlockStorageMetadata>()

    override suspend fun markBlocks(blockHeights: Iterable<Long>): WriteResult = WriteResult.DEFAULT
}