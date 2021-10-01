package io.provenance.aggregate.service.aws

import com.timgroup.statsd.StatsDClient
import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.Environment
import io.provenance.aggregate.service.S3Config
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamo
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.s3.AwsS3
import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.logger
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import kotlin.time.ExperimentalTime
import java.time.Duration as JavaDuration

@OptIn(ExperimentalTime::class)
abstract class AwsInterface(val s3Config: S3Config, val dynamoConfig: DynamoConfig, val dogStatsClient: StatsDClient) {

    companion object {
        val DEFAULT_REGION: Region = Region.US_EAST_1

        fun create(environment: Environment, s3Config: S3Config, dynamoConfig: DynamoConfig, dogStatsClient: StatsDClient): AwsInterface {
            return when (environment) {
                Environment.local -> LocalStackAwsInterface(s3Config, dynamoConfig, dogStatsClient)
                Environment.development -> DefaultAwsInterface(s3Config, dynamoConfig, dogStatsClient)
                Environment.production -> DefaultAwsInterface(s3Config, dynamoConfig, dogStatsClient)
            }
        }
    }

    private val log = logger()

    protected open fun getS3WriteTimeout(): JavaDuration = JavaDuration.ZERO

    protected open fun getS3MaxConcurrency(): Int = 64

    protected open fun getRegion(): Region = s3Config.region?.let { Region.of(it) } ?: DEFAULT_REGION

    protected open fun getCredentialsProvider(): AwsCredentialsProvider = DefaultCredentialsProvider.create()

    protected open fun getEndpointOverride(): URI? = null

    val s3Client: S3AsyncClient by lazy {
        createS3Client()
    }

    val dynamoClient: DynamoDbAsyncClient by lazy {
        createDynamoDbClient()
    }

    private fun createNettyClient(): SdkAsyncHttpClient {
        return NettyNioAsyncHttpClient.builder()
            .writeTimeout(getS3WriteTimeout())
            .maxConcurrency(getS3MaxConcurrency())
            .build()
    }

    private fun createS3Client(): S3AsyncClient {
        val override = getEndpointOverride()
        val httpClient = createNettyClient()
        return S3AsyncClient.builder()
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .region(getRegion())
            .httpClient(httpClient)
            .serviceConfiguration(
                S3Configuration.builder()
                    .checksumValidationEnabled(false)
                    .chunkedEncodingEnabled(true)
                    // See https://github.com/localstack/localstack/blob/master/README.md#troubleshooting
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .apply {
                if (override != null) {
                    log.info("Using endpoint override: $override")
                    endpointOverride(override)
                }
            }
            .build()
    }

    private fun createDynamoDbClient(): DynamoDbAsyncClient {
        val override = getEndpointOverride()
        val httpClient = createNettyClient()
        return DynamoDbAsyncClient.builder()
            .credentialsProvider(getCredentialsProvider())
            .region(getRegion())
            .httpClient(httpClient)
            .apply {
                if (override != null) {
                    log.info("Using endpoint override: $override")
                    endpointOverride(override)
                }
            }
            .build()
    }

    open fun s3(): AwsS3Interface {
        return AwsS3(s3Client, s3Config.bucket)
    }

    open fun dynamo(): AwsDynamoInterface {
        return AwsDynamo(
            dynamoClient,
            dynamoConfig.blockBatchTable,
            dynamoConfig.blockMetadataTable,
            dynamoConfig.serviceMetadataTable,
            dogStatsClient,
        )
    }
}
