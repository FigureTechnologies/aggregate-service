package io.provenance.aggregate.service.mocks

import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.S3Config
import io.provenance.aggregate.service.aws.LocalStackAwsInterface
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.dynamodb.DynamoTable
import io.provenance.aggregate.service.aws.s3.AwsS3Interface

open class MockAwsInterface protected constructor(s3Config: S3Config, dynamoConfig: DynamoConfig) :
    LocalStackAwsInterface(s3Config, dynamoConfig) {

    class Builder() {
        var s3Impl: AwsS3Interface? = null
        var dynamoImpl: AwsDynamoInterface? = null

        fun <I : AwsS3Interface> s3Implementation(impl: I) = apply { s3Impl = impl }
        fun <I : AwsDynamoInterface> dynamoImplementation(impl: I) = apply { dynamoImpl = impl }

        fun build(s3Config: S3Config, dynamoConfig: DynamoConfig): MockAwsInterface {
            return object : MockAwsInterface(s3Config, dynamoConfig) {
                override fun s3(): AwsS3Interface {
                    return s3Impl ?: LocalStackS3(s3Client, s3Config.bucket)
                }

                override fun dynamo(): AwsDynamoInterface {
                    return dynamoImpl ?: LocalStackDynamo(
                        dynamoClient,
                        dynamoConfig.blockBatchTable,
                        dynamoConfig.blockMetadataTable,
                        dynamoConfig.serviceMetadataTable
                    )
                }
            }
        }
    }

    companion object {
        fun builder() = Builder()
    }
}