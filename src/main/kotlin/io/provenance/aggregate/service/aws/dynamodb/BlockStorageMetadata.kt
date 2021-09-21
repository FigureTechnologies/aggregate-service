package io.provenance.aggregate.service.aws.dynamodb

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

// See documentation for the enhanced DynamoDB "enhanced" async client:
// https://github.com/aws/aws-sdk-java-v2/tree/master/services-custom/dynamodb-enhanced
//
// For working with immutable data, see the workaround:
// https://github.com/aws/aws-sdk-java-v2/issues/2096#issuecomment-752667521
@DynamoDbImmutable(builder = BlockStorageMetadata.Builder::class)
class BlockStorageMetadata(
    @get:DynamoDbAttribute(value="BlockHeight")  // AWS conventions dictate upper-case camelCase
    @get:DynamoDbPartitionKey val blockHeight: Long
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var blockHeight: Long? = null
        fun blockHeight(value: Long) = apply { blockHeight = value }

        fun build() = BlockStorageMetadata(
            blockHeight = blockHeight!!
        )
    }
}
