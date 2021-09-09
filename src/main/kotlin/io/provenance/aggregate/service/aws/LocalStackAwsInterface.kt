package io.provenance.aggregate.service.aws

import cloud.localstack.Localstack
import cloud.localstack.docker.annotation.LocalstackDockerConfiguration
import com.squareup.moshi.Moshi
import io.provenance.aggregate.service.S3Config
import io.provenance.aggregate.service.aws.AwsInterface.Companion.DEFAULT_REGION
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.future.await
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import java.net.URI

open class LocalStackAwsInterface(s3Config: S3Config) : AwsInterface(s3Config) {

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

    private val log = logger()

    /**
     * It is necessary to override the external hostname of the LocalStack container, otherwise
     * localhost.localstack.cloud will be used. In order to avoid having to add that entry to /etc/hosts,
     * we can just bind to 0.0.0.0 (the default external host in our case) and move on:
     */
    private suspend fun startLocalStack(withServices: List<String>, externalHostname: String = "0.0.0.0") {
        val enableServices = withServices.joinToString(separator = ",")
        log.info("SERVICES = ${enableServices}")
        if (Localstack.INSTANCE.isRunning) {
            log.info("Localstack already running")
        } else {
            val config = LocalstackDockerConfiguration
                .builder()
                .environmentVariables(mapOf("SERVICES" to enableServices))
                .externalHostName(externalHostname)  // override, otherwise "localhost.localstack.cloud" will be used
                .build()
            Localstack.INSTANCE.startup(config)
        }
        s3Client.createBucket(
            CreateBucketRequest.builder()
                .bucket(s3Config.bucket)
                .build()
        )
            .await().also {
                log.info("Creating S3 bucket: \"${s3Config.bucket}\"")
            }
    }
}
