package io.provenance.aggregate.service.aws.dynamodb.extensions

import io.provenance.aggregate.service.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.service.stream.batch.BatchId
import io.provenance.aggregate.service.stream.models.StreamBlock

fun StreamBlock.toBlockStorageMetadata(batchId: BatchId): BlockStorageMetadata? {
    if (this.height != null) {
        return BlockStorageMetadata(
            blockHeight = this.height!!,
            batchId = batchId.toString()
        )
    }
    return null
}