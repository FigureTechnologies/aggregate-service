package tech.figure.aggregate.repository.database.dynamo

import tech.figure.aggregate.common.utils.timestamp
import tech.figure.aggregate.repository.database.dynamo.BlockStorageMetadata.Builder
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

/**
 * A mapping to the Dynamo table defined in [src/main/resources/application.yml] as `aws.dynamo.block-metadata-table`
 *
 * Documentation for the enhanced DynamoDB "enhanced" async client:
 * @see https://github.com/aws/aws-sdk-java-v2/tree/master/services-custom/dynamodb-enhanced
 *
 * Resources for Working with immutable data:
 * @see https://github.com/aws/aws-sdk-java-v2/issues/2096#issuecomment-752667521
 *
 * Unfortunately, data classes are not supported. Attempting to use a data class will result in the error:
 *
 * ```
 * Exception in thread "main" java.lang.IllegalArgumentException: A method was found on the immutable class that does
 * not appear to have a matching setter on the builder class. Use the @DynamoDbIgnore annotation on the method if you
 * do not want it to be included in the TableSchema introspection.
 *
 * [Method = "public final long tech.figure.aggregate.common.aws.dynamodb.BlockStorageMetadata.component1()"]
 * ```
 *
 * @constructor Invoked by the AWS SDK, instantiating the inner builder class [BlockStorageMetadata.Builder] as part of
 * construction.
 */
@DynamoDbImmutable(builder = Builder::class)
class BlockStorageMetadata(
    @get:DynamoDbAttribute(value = "BlockHeight")  // AWS conventions dictate upper-case camelCase
    @get:DynamoDbPartitionKey val blockHeight: Long,
    @get:DynamoDbAttribute(value = "BatchId") val batchId: String,
    @get:DynamoDbAttribute(value = "UpdatedAt") val updatedAt: String
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var blockHeight: Long? = null
        private var batchId: String? = null
        private var updatedAt: String = timestamp()

        fun blockHeight(_blockHeight: Long) = apply { blockHeight = _blockHeight }

        fun batchId(_batchId: String) = apply { batchId = _batchId }

        fun updatedAt(_updatedAt: String) = apply { updatedAt = _updatedAt }

        fun build() = BlockStorageMetadata(
            blockHeight = blockHeight ?: error("required block height not set"),
            batchId = batchId ?: error("required batch ID not set"),
            updatedAt = updatedAt
        )
    }

    override fun toString(): String = "BlockStorageMetadata { blockHeight: $blockHeight, batchId = $batchId }"
}
