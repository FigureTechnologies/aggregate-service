package io.provenance.aggregate.service.aws.dynamodb.extensions

import io.provenance.aggregate.service.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.service.stream.batch.BatchId
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.utils.timestamp

fun StreamBlock.toBlockStorageMetadata(batchId: BatchId): BlockStorageMetadata? =
    this.height
        ?.let { height: Long ->
            BlockStorageMetadata(
                blockHeight = height,
                batchId = batchId.toString(),
                updatedAt = timestamp()
            )
        }