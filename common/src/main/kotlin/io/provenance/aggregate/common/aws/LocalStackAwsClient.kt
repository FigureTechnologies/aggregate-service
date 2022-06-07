package io.provenance.aggregate.common.aws

import io.provenance.aggregate.common.DynamoConfig
import io.provenance.aggregate.common.S3Config
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.net.URI

/**
 * The client provider to use when interacting with the LocalStack emulation of AWS.
 *
 * @property s3Client The AWS SDK S3 async client to use.
 * @property bucket The S3 bucket to write results to.
 */
open class LocalStackAwsClient(s3Config: S3Config) :
    AwsClient(s3Config) {

    companion object {
        const val LOCALSTACK_ACCESS_TOKEN = "test"
        const val LOCALSTACK_SECRET_TOKEN = "test"
        const val S3_ENDPOINT_OVERRIDE = "http://localhost:4566"
    }

    override fun getCredentialsProvider(): StaticCredentialsProvider =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(LOCALSTACK_ACCESS_TOKEN, LOCALSTACK_SECRET_TOKEN))

    override fun getEndpointOverride(): URI? =
        System.getenv("S3_ENDPOINT_OVERRIDE")?.let { URI(it) } ?: URI(S3_ENDPOINT_OVERRIDE)
}
