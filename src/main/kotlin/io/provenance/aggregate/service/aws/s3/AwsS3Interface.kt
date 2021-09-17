package io.provenance.aggregate.service.aws.s3

import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectResponse

interface AwsS3Interface {
    suspend fun streamObject(obj: S3StreamableObject): PutObjectResponse = streamObject(obj.key, obj.body)
    suspend fun streamObject(key: String, body: AsyncRequestBody): PutObjectResponse
}