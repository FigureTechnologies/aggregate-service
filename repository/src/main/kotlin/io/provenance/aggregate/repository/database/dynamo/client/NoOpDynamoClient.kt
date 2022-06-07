package io.provenance.aggregate.repository.database.dynamo.client

import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.repository.database.dynamo.BlockBatch
import io.provenance.aggregate.repository.database.dynamo.BlockStorageMetadata
import io.provenance.aggregate.repository.database.dynamo.WriteResult
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

//    override suspend fun getMaxHistoricalBlockHeight(): Long? = null
//
//    override suspend fun writeMaxHistoricalBlockHeight(blockHeight: Long): WriteResult = WriteResult.empty()
}
