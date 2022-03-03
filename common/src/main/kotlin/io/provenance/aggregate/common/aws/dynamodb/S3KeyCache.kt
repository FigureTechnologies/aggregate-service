package io.provenance.aggregate.common.aws.dynamodb

import io.provenance.aggregate.common.utils.timestamp
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbImmutable(builder = S3KeyCache.Builder::class)
class S3KeyCache(
    @get:DynamoDbPartitionKey val batchId: String,
    @get:DynamoDbAttribute(value = "processed") val processed: Boolean = false,
    @get:DynamoDbAttribute(value = "S3Key") val s3Key: String?,
    @get:DynamoDbAttribute(value = "UpdatedAt") val updatedAt: String = timestamp()
) {

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var batchId: String? = null
        private var processed: Boolean = false
        private var s3Key: String? = null
        private var updatedAt: String = timestamp()

        fun batchId(id: String) = apply { batchId = id }

        fun processed(_processed: Boolean) = apply { processed = _processed }

        fun s3Key(key: String) = apply { s3Key = key }

        fun updatedAt(_updatedAt: String) = apply { updatedAt = _updatedAt }

        fun build() = S3KeyCache(
            batchId = batchId ?: error("required batch ID"),
            processed = processed,
            s3Key = s3Key,
            updatedAt = updatedAt
        )
    }

    override fun toString(): String = "S3KeyCache { processed: $processed, batchId = $batchId, s3Key = $s3Key }"
}
