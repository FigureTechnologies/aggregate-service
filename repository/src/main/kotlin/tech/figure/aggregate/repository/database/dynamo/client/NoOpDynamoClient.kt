package tech.figure.aggregate.repository.database.dynamo.client

import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.repository.database.dynamo.BlockBatch
import tech.figure.aggregate.repository.database.dynamo.BlockStorageMetadata
import tech.figure.aggregate.repository.database.dynamo.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A DynamoDB client that does nothing. All methods return null or an empty result.
 */
class NoOpDynamoClient : IDynamoClient {

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? = null

    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> =
        emptyFlow()

    override suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult =
        WriteResult.empty()
}
