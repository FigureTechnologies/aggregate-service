package io.provenance.aggregate.service.test.mocks

import io.provenance.aggregate.common.DynamoConfig
import io.provenance.aggregate.common.S3Config
import io.provenance.aggregate.common.aws.LocalStackAwsClient
import io.provenance.aggregate.common.aws.dynamodb.client.DynamoClient
import io.provenance.aggregate.common.aws.s3.client.S3Client
import io.provenance.aggregate.service.test.utils.Defaults

open class MockAwsClient protected constructor(s3Config: S3Config, dynamoConfig: DynamoConfig) :
    LocalStackAwsClient(s3Config, dynamoConfig) {

    class Builder() {
        var s3Impl: S3Client? = null
        var dynamoImpl: DynamoClient? = null

        fun <I : S3Client> s3Implementation(impl: I) = apply { s3Impl = impl }
        fun <I : DynamoClient> dynamoImplementation(impl: I) = apply { dynamoImpl = impl }

        fun build(
            s3Config: S3Config = Defaults.s3Config,
            dynamoConfig: DynamoConfig = Defaults.dynamoConfig
        ): MockAwsClient {
            return object : MockAwsClient(s3Config, dynamoConfig) {
                override fun s3(): S3Client {
                    return s3Impl ?: LocalStackS3(s3Client, s3Config.bucket)
                }

                override fun dynamo(): DynamoClient {
                    return dynamoImpl ?: LocalStackDynamoClient(
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
