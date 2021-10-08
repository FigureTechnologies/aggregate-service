package io.provenance.aggregate.service.aws.dynamodb

import io.provenance.aggregate.service.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoOpDynamo : AwsDynamoInterface {

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? = null

    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> =
        emptyFlow()

    override suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult =
        WriteResult.empty()

    override suspend fun getMaxHistoricalBlockHeight(): Long? = null

    override suspend fun writeMaxHistoricalBlockHeight(blockHeight: Long): WriteResult = WriteResult.empty()
}