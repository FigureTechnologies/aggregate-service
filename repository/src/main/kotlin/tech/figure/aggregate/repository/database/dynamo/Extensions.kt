package tech.figure.aggregate.repository.database.dynamo

import tech.figure.aggregate.common.models.BatchId
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.common.utils.timestamp

fun StreamBlock.toBlockStorageMetadata(batchId: BatchId): BlockStorageMetadata? =
    this.height
        ?.let { height: Long ->
            BlockStorageMetadata(
                blockHeight = height,
                batchId = batchId.toString(),
                updatedAt = timestamp()
            )
        }
