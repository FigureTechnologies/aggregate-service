package io.provenance.aggregate.service.aws.s3

import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectResponse

interface AwsS3Interface {
    suspend fun streamObject(obj: StreamableObject): PutObjectResponse = streamObject(obj.key, obj.body, obj.metadata)
    suspend fun streamObject(
        key: S3Key,
        body: AsyncRequestBody,
        metadata: Map<String, String>? = null
    ): PutObjectResponse
}