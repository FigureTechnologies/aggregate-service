package io.provenance.aggregate.common.aws.dynamodb.client

import io.provenance.aggregate.common.aws.dynamodb.BlockBatch
import io.provenance.aggregate.common.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.common.aws.dynamodb.WriteResult
import io.provenance.aggregate.common.models.StreamBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A DynamoDB client that does nothing. All methods return null or an empty result.
 */
class NoOpDynamoClient : DynamoClient {

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? = null

    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> =
        emptyFlow()

    override suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult =
        WriteResult.empty()

    override suspend fun getMaxHistoricalBlockHeight(): Long? = null

    override suspend fun writeMaxHistoricalBlockHeight(blockHeight: Long): WriteResult = WriteResult.empty()

    override suspend fun writeS3KeyCache(batchId: String, s3Key: String) { /* no-op */ }
}
