package io.provenance.aggregate.service.aws.s3

import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import kotlinx.coroutines.future.await

open class AwsS3(protected val s3Client: S3AsyncClient, protected val bucket: S3Bucket) : AwsS3Interface {
    override suspend fun streamObject(
        key: S3Key,
        body: AsyncRequestBody,
        metadata: Map<String, String>?
    ): PutObjectResponse {
        return s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket(bucket.name)
                .key(key.value)
                .apply {
                    if (metadata != null) {
                        metadata(metadata)
                    }
                }
                .build(),
            body
        ).await()
    }
}