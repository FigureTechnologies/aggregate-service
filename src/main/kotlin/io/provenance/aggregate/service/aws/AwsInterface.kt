package io.provenance.aggregate.service.aws

import io.provenance.aggregate.service.Environment
import io.provenance.aggregate.service.S3Config
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.net.URI
import java.time.Duration

interface S3StreamableObject {
    val key: String
    val body: AsyncRequestBody
}

abstract class AwsInterface(val s3Config: S3Config) {
    companion object {
        val DEFAULT_REGION = Region.US_EAST_1

        fun create(environment: Environment, s3Config: S3Config): AwsInterface {
            return when (environment) {
                Environment.local -> LocalStackAwsInterface(s3Config)
                Environment.development -> DefaultAwsInterface(s3Config)
                Environment.production -> DefaultAwsInterface(s3Config)
            }
        }
    }

    private val log: Logger

    init {
        log = logger()
    }

    protected open fun getS3WriteTimeout(): Duration = Duration.ZERO

    protected open fun getS3MaxConcurrency(): Int = 64

    protected open fun getRegion(): Region = s3Config.region?.let { Region.of(it) } ?: DEFAULT_REGION

    protected open fun getCredentialsProvider(): AwsCredentialsProvider = DefaultCredentialsProvider.create()

    protected open fun getEndpointOverride(): URI? = null

    val s3Client: S3AsyncClient

    init {
        s3Client = createS3Client()
    }

    private fun createS3Client(): S3AsyncClient {
        val s3Override = getEndpointOverride()
        val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder()
            .writeTimeout(getS3WriteTimeout())
            .maxConcurrency(getS3MaxConcurrency())
            .build()
        val s3Client = S3AsyncClient.builder()
            .credentialsProvider(getCredentialsProvider())
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
                if (s3Override != null) {
                    log.info("Using endpoint override: $s3Override")
                    it.endpointOverride(s3Override)
                } else {
                    it
                }
            }
            .build()
        return s3Client
    }

    suspend fun streamObject(obj: S3StreamableObject): PutObjectResponse =
        streamObject(Bucket(s3Config.bucket), obj.key, obj.body)

    suspend fun streamObject(key: String, body: AsyncRequestBody): PutObjectResponse =
        streamObject(Bucket(s3Config.bucket), key, body)

    suspend fun streamObject(bucket: Bucket, key: String, body: AsyncRequestBody): PutObjectResponse {
        return s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket(bucket.name)
                .key(key)
                .build(),
            body
        ).await()
    }
}