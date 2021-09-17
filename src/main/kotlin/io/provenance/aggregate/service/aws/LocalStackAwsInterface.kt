package io.provenance.aggregate.service.aws

import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.S3Config
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import java.net.URI

open class LocalStackAwsInterface(s3Config: S3Config, dynamoConfig: DynamoConfig) :
    AwsInterface(s3Config, dynamoConfig) {

    companion object {
        const val LOCALSTACK_ACCESS_TOKEN = "test"
        const val LOCALSTACK_SECRET_TOKEN = "test"
        const val S3_ENDPOINT_OVERRIDE = "http://localhost:4566"
    }

    override fun getRegion(): Region = s3Config.region?.let { Region.of(it) } ?: DEFAULT_REGION

    override fun getCredentialsProvider(): StaticCredentialsProvider =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(LOCALSTACK_ACCESS_TOKEN, LOCALSTACK_SECRET_TOKEN))

    override fun getEndpointOverride(): URI? =
        System.getenv("S3_ENDPOINT_OVERRIDE")?.let { URI(it) } ?: URI(S3_ENDPOINT_OVERRIDE)
}
