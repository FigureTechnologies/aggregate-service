package tech.figure.aggregate.common.aws.s3.client

import tech.figure.aggregate.common.aws.s3.S3Key
import tech.figure.aggregate.common.aws.s3.StreamableObject
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectResponse

/**
 * The interface for clients that will interact with S3.
 */
interface S3Client {
    /**
     * Stream a single object to S3.
     *
     * @property obj An object implementing the [StreamableObject] interface, which contains the information necessary
     * to upload an object to S3 (key, data, metadata).
     */
    suspend fun streamObject(obj: StreamableObject): PutObjectResponse = streamObject(obj.key, obj.body, obj.metadata)

    /**
     * Stream a single object to S3.
     *
     * @property key The key the object will be stored under on S3.
     * @property body An asynchronous provider for the object's data.
     * @property metadata Optional metadata to associate with the object on S3.
     */
    suspend fun streamObject(
        key: S3Key,
        body: AsyncRequestBody,
        metadata: Map<String, String>? = null
    ): PutObjectResponse
}
