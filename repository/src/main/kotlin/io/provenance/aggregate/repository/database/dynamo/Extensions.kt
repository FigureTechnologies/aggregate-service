package io.provenance.aggregate.repository.database.dynamo

import io.provenance.aggregate.common.models.BatchId
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.utils.timestamp

fun StreamBlock.toBlockStorageMetadata(batchId: BatchId): BlockStorageMetadata? =
    this.height
        ?.let { height: Long ->
            BlockStorageMetadata(
                blockHeight = height,
                batchId = batchId.toString(),
                updatedAt = timestamp()
            )
        }
