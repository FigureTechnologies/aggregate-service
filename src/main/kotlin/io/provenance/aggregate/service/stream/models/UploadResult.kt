package io.provenance.aggregate.service.stream.models

import io.provenance.aggregate.service.stream.batch.BatchId

data class UploadResult(
    val batchId: BatchId,
    val batchSize: Int,
    val eTag: String,
    val s3Key: String
)