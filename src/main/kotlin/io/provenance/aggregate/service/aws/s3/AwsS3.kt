package io.provenance.aggregate.service.aws.s3

import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import kotlinx.coroutines.future.await

open class AwsS3(protected val s3Client: S3AsyncClient, protected val bucket: Bucket) : AwsS3Interface {
    override suspend fun streamObject(key: String, body: AsyncRequestBody): PutObjectResponse {
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