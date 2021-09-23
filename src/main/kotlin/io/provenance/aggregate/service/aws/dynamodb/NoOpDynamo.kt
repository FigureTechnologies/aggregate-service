package io.provenance.aggregate.service.aws.dynamodb

import io.provenance.aggregate.service.stream.batch.BatchId
import io.provenance.aggregate.service.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoOpDynamo : AwsDynamoInterface {

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? = null

    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> =
        emptyFlow()

    override suspend fun trackBlocks(batchId: BatchId, blocks: Iterable<StreamBlock>): WriteResult =
        WriteResult.empty()
}