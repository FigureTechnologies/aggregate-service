package io.provenance.aggregate.common.models

import io.provenance.aggregate.common.aws.s3.S3Key
import io.provenance.aggregate.common.models.BatchId

/**
 * Represents the result of uploading a block to S3.
 *
 * @property batchId The ID of the batch the block is assigned to.
 * @property batchSize The size of the batch that the block was included in.
 * @property eTag The ETag returned from AWS as a result of the upload.
 * @property s3Key The key specifying the location of the object on S3.
 */
data class UploadResult(
    val batchId: BatchId,
    val batchSize: Int,
    val eTag: String,
    val s3Key: S3Key,
    val blockHeightRange: String
)
