package io.provenance.aggregate.service.aws.dynamodb

import io.provenance.aggregate.service.aws.s3.S3Bucket
import io.provenance.aggregate.service.aws.s3.S3Key
import io.provenance.aggregate.service.stream.batch.BatchId
import io.provenance.aggregate.service.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.fold
import okhttp3.internal.toImmutableMap

interface AwsDynamoInterface {

    /**
     * Given a block height, fetch any storage metadata associated with it.
     *
     * @param blockHeight The height of the block to fetch metadata for
     */
    suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata?

    /**
     * Given an Iterable of block heights, fetch any storage metadata found for heights.
     *
     * @param blockHeights An Iterable of block heights
     */
    suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata>

    /**
     * Produce a map of block height to storage metadata for any previously recorded blocks.
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
     * @param batch The batch the blocks are a part of.
     * @param blocks The blocks used to generate the objects that were uploaded.
     */
    suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult

    /**
     * Fetch the maximum block height recorded for a historical block.
     */
    suspend fun getMaxHistoricalBlockHeight(): Long?

    /**
     * Record the highest historical block seen.
     */
    suspend fun writeMaxHistoricalBlockHeight(blockHeight: Long): WriteResult
}