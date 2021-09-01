package io.provenance.aggregate.service.aws

import cloud.localstack.Localstack
import cloud.localstack.docker.annotation.LocalstackDockerConfiguration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class AwsInterface {

    companion object {
        val LOCALSTACK_ACCESS_TOKEN = "test"
        val LOCALSTACK_SECRET_TOKEN = "test"
        val REGION = Region.US_EAST_1
    }

    fun startLocalstackContainer() {
        Localstack.INSTANCE.startup(LocalstackDockerConfiguration.DEFAULT)
        val bucketName = SimpleDateFormat("yyyyMMdd").format(Date())
        createS3Bucket(
            Bucket(bucketName),
            Localstack.INSTANCE.endpointS3
        ).also { println("bucketName == $bucketName") }
    }

    fun s3Client(endpointOverride: String? = null): S3Client {
        return if(endpointOverride == null) {
            S3Client.builder()
                .region(REGION)
                .build()
        } else {
            S3Client.builder()
                .endpointOverride(URI(endpointOverride))
                .region(REGION)
                .build()
        }
    }

    fun createS3Bucket(bucket: Bucket, endpointOverride: String? = null){
            // todo: check if bucket already exist
            s3Client(endpointOverride).createBucket(
                CreateBucketRequest.builder()
                    .bucket(bucket.name)
                    .build()
            )
    }

    fun deleteS3Bucket(){
        //todo: determine if this is really needed at all
    }

    fun deleteObject(){
        //todo: determine if this is really need at all
    }

    fun putObject(bucket: Bucket, key: UUID, data: ByteArray) {
        val objRequest = PutObjectRequest.builder()
            .bucket(bucket.name)
            .key(key.toString())
            .build()

        val requestBody = RequestBody.fromBytes(data)

        s3Client().putObject(objRequest, requestBody)
    }

    private fun awsBasicCredential() = AwsBasicCredentials.create(LOCALSTACK_ACCESS_TOKEN, LOCALSTACK_SECRET_TOKEN)

}
