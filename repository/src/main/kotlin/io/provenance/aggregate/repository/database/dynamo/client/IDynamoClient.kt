package io.provenance.aggregate.repository.database.dynamo.client

import io.provenance.aggregate.repository.database.dynamo.WriteResult
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.repository.database.dynamo.BlockBatch
import io.provenance.aggregate.repository.database.dynamo.BlockStorageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.fold
import okhttp3.internal.toImmutableMap

/**
 * A service-specific client for interacting with AWS DynamoDB.
 */
interface IDynamoClient {

    /**
     * Given a block height, fetch any storage metadata associated with it.
     *
     * @property blockHeight The height of the block to fetch metadata for
     */
    suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata?

    /**
     * Given an Iterable of block heights, fetch any storage metadata found for heights.
     *
     * @property blockHeights An Iterable of block heights
     */
    suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata>

    /**
     * Produce a map of block height to storage metadata for any previously recorded blocks.
     *
     * @property blockHeights The block heights to store
     * @return a mapping of block heights to [BlockStorageMetadata], a data class which stored metadata about the
     * block
     */
    suspend fun getBlockMetadataMap(blockHeights: Iterable<Long>): Map<Long, BlockStorageMetadata> =
        getBlockMetadata(blockHeights).fold(mutableMapOf<Long, BlockStorageMetadata>()) { mapping: MutableMap<Long, BlockStorageMetadata>, metadata: BlockStorageMetadata ->
            mapping[metadata.blockHeight] = metadata
            mapping
        }
            .toImmutableMap()

    /**
     * Mark blocks as being seen and associate them with a given batch.
     *
     * @property batch The batch the blocks are a part of.
     * @property blocks The blocks used to generate the objects that were uploaded.
     * @return the result of the write operation
     */
    suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult

}
