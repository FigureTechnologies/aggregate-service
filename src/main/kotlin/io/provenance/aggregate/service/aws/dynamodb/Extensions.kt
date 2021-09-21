package io.provenance.aggregate.service.aws.dynamodb.extensions

import io.provenance.aggregate.service.aws.dynamodb.BlockStorageMetadata
import io.provenance.aggregate.service.stream.models.StreamBlock

fun StreamBlock.toBlockStorageMetadata(): BlockStorageMetadata? {
    if (this.height != null) {
        return BlockStorageMetadata(
            blockHeight = this.height!!
        )
    }
    return null
}