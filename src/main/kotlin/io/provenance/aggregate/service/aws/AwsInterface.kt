package io.provenance.aggregate.service.aws

import com.squareup.moshi.JsonDataException
import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.Environment
import io.provenance.aggregate.service.S3Config
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamo
import io.provenance.aggregate.service.aws.dynamodb.AwsDynamoInterface
import io.provenance.aggregate.service.aws.dynamodb.Table
import io.provenance.aggregate.service.aws.s3.AwsS3
import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.aws.s3.Bucket
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import java.lang.Math.pow
import java.net.ConnectException
import java.net.URI
import kotlin.math.pow
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun <T> attempt(maxTries: Int, retryOn: Set<Class<*>> = setOf(ConnectException::class.java), create: () -> T): T =
    runBlocking<T> {
        flowOf(create())
            .retryWhen { cause: Throwable, attempt: Long ->
                println("Retrying attempt:$attempt")
                if (maxTries < attempt && cause::class.java in retryOn) {
                    delay(Duration.milliseconds(1000.0 * 2.0.pow(attempt.toDouble())))
                    true
                } else {
                    false
                }
            }
            .single()
    }

@OptIn(ExperimentalTime::class)
abstract class AwsInterface(val s3Config: S3Config, val dynamoConfig: DynamoConfig) {

    companion object {
        val DEFAULT_REGION = Region.US_EAST_1

        fun create(environment: Environment, s3Config: S3Config, dynamoConfig: DynamoConfig): AwsInterface {
            return when (environment) {
                Environment.local -> LocalStackAwsInterface(s3Config, dynamoConfig)
                Environment.development -> DefaultAwsInterface(s3Config, dynamoConfig)
                Environment.production -> DefaultAwsInterface(s3Config, dynamoConfig)
            }
        }
    }

    private val log: Logger

    init {
        log = logger()
    }


    protected open fun getS3WriteTimeout(): JavaDuration = JavaDuration.ZERO

    protected open fun getS3MaxConcurrency(): Int = 64

    protected open fun getRegion(): Region = s3Config.region?.let { Region.of(it) } ?: DEFAULT_REGION

    protected open fun getCredentialsProvider(): AwsCredentialsProvider = EnvironmentVariableCredentialsProvider()

    protected open fun getEndpointOverride(): URI? = null

    val s3Client: S3AsyncClient by lazy {
        attempt(10) { createS3Client() }
    }

    val s3: AwsS3Interface by lazy {
        attempt(10) { AwsS3(s3Client, Bucket(s3Config.bucket)) }
    }

    val dynamoClient: DynamoDbAsyncClient by lazy {
        attempt(10) { createDynamoDbClient() }
    }

    val dynamo: AwsDynamoInterface by lazy {
        attempt(10) { AwsDynamo(dynamoClient, Table(dynamoConfig.blockMetadataTable)) }
    }

//    init {
//        s3Client = createS3Client()
//        s3 = AwsS3(s3Client, Bucket(s3Config.bucket))
//        dynamoClient = createDynamoDbClient()
//        dynamo = AwsDynamo(dynamoClient, Table(dynamoConfig.blockMetadataTable))
//    }

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
            .let {
                if (override != null) {
                    log.info("Using endpoint override: $override")
                    it.endpointOverride(override)
                } else {
                    it
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
            .let {
                if (override != null) {
                    log.info("Using endpoint override: $override")
                    it.endpointOverride(override)
                } else {
                    it
                }
            }
            .build()
    }

    open fun s3(): AwsS3Interface {
        return AwsS3(s3Client, Bucket(s3Config.bucket))
    }

    open fun dynamo(): AwsDynamoInterface {
        return AwsDynamo(dynamoClient, Table(dynamoConfig.blockMetadataTable))
    }
}
