package tech.figure.aggregate.common.aws.s3.client

import tech.figure.aggregate.common.aws.s3.S3Bucket
import tech.figure.aggregate.common.aws.s3.S3Key
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import kotlinx.coroutines.future.await

/**
 * The default implementation of the AWS S3 client wrapper.
 *
 * @property s3Client The AWS SDK S3 async client to use.
 * @property bucket The S3 bucket to write results to.
 */
open class DefaultS3Client(protected val s3Client: S3AsyncClient, protected val bucket: S3Bucket) : S3Client {
    override suspend fun streamObject(
        key: S3Key,
        body: AsyncRequestBody,
        metadata: Map<String, String>?
    ): PutObjectResponse {
        return s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket(bucket.value)
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
