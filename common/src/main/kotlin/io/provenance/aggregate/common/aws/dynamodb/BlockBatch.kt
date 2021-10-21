package io.provenance.aggregate.common.aws.dynamodb

import io.provenance.aggregate.common.aws.s3.S3Bucket
import io.provenance.aggregate.common.aws.s3.S3Key
import io.provenance.aggregate.common.models.BatchId
import io.provenance.aggregate.common.utils.timestamp
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

/**
 * A mapping to the Dynamo table defined in [src/main/resources/application.yml] as `aws.dynamo.block-batch-table`
 *
 * Documentation for the enhanced DynamoDB "enhanced" async client:
 * @see https://github.com/aws/aws-sdk-java-v2/tree/master/services-custom/dynamodb-enhanced
 *
 * Resources for Working with immutable data:
 * @see https://github.com/aws/aws-sdk-java-v2/issues/2096#issuecomment-752667521
 *
 * @constructor Invoked by the AWS SDK, instantiating the inner builder class [BlockBatch.Builder] as part of
 * construction.
 */
@DynamoDbImmutable(builder = BlockBatch.Builder::class)
class BlockBatch(
    @get:DynamoDbAttribute(value = "BatchId")
    @get:DynamoDbPartitionKey val batchId: String,
    @get:DynamoDbAttribute(value = "S3Bucket") val s3Bucket: String,
    @get:DynamoDbAttribute(value = "S3Keys") val s3Keys: List<String>,
    @get:DynamoDbAttribute(value = "UpdatedAt") val updatedAt: String
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()

        @JvmStatic
        operator fun invoke(batchId: BatchId, s3Bucket: S3Bucket, s3Keys: Iterable<S3Key>): BlockBatch {
            return BlockBatch(
                batchId.value,
                s3Bucket.name,
                s3Keys.map { it.value },
                timestamp()
            )
        }
    }

    class Builder {
        private var batchId: String? = null
        private var s3Bucket: String? = null
        private var s3Keys: List<String> = emptyList()
        private var updatedAt: String = timestamp()

        fun batchId(id: String) = apply { batchId = id }

        fun s3Bucket(bucket: String) = apply { s3Bucket = bucket }

        fun s3Keys(keys: List<String>) = apply { s3Keys = keys }

        fun updatedAt(_updatedAt: String) = apply { updatedAt = _updatedAt }

        fun build() = BlockBatch(
            batchId = batchId ?: error("required batch ID not set"),
            s3Bucket = s3Bucket ?: error("required S3 bucket not set"),
            s3Keys = s3Keys,
            updatedAt = updatedAt
        )
    }

    override fun toString(): String =
        "BlockBatch { batchId = $batchId, s3Bucket = ${s3Bucket}, s3Keys = [${s3Keys.joinToString(",")}] }"
}
