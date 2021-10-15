package io.provenance.aggregate.service.aws.dynamodb

import io.provenance.aggregate.service.utils.timestamp
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

/**
 * A mapping to the Dynamo table defined in [src/main/resources/application.yml] as `aws.dynamo.service-metadata-table`
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
@DynamoDbImmutable(builder = ServiceMetadata.Builder::class)
class ServiceMetadata(
    @get:DynamoDbAttribute(value = "Property")
    @get:DynamoDbPartitionKey val property: String,
    @get:DynamoDbAttribute(value = "Value") val value: String,
    @get:DynamoDbAttribute(value = "UpdatedAt") val updatedAt: String
) {
    sealed class Properties(val key: String) {
        /**
         * Create a new [ServiceMetadata] instance for the Property (using the given key value)
         *
         * @property value The value of the [ServiceMetadata] instance.
         */
        fun newEntry(value: String): ServiceMetadata =
            ServiceMetadata(property = key, value = value, updatedAt = timestamp())

        /**
         * References the current historical maximum block height seen so far.
         */
        object MaxHistoricalBlockHeight : Properties("MaxHistoricalBlockHeight")

        override fun toString() = key
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var property: String? = null
        private var value: String? = null
        private var updatedAt: String = timestamp()

        fun property(_property: String) = apply { property = _property }

        fun value(_value: String) = apply { value = _value }

        fun updatedAt(_updatedAt: String) = apply { updatedAt = _updatedAt }

        fun build() = ServiceMetadata(
            property = property ?: error("required property name not set"),
            value = value ?: error("required property value not set"),
            updatedAt = updatedAt
        )
    }

    override fun toString(): String = "<<Property: $property; Value: $value; UpdatedAt: $updatedAt>>"
}
