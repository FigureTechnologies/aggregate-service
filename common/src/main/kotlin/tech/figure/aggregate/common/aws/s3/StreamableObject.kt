package tech.figure.aggregate.common.aws.s3

import software.amazon.awssdk.core.async.AsyncRequestBody

/**
 * Any type that implements this interface can be streamed as a single object to AWS S3.
 */
interface StreamableObject {
    /**
     * The key of the object.
     */
    val key: S3Key

    /**
     * The body of the object, as consumed by the AWS SDK v2.
     */
    val body: AsyncRequestBody

    /**
     * Optional metadata to associate with the object on S3.
     */
    val metadata: Map<String, String>?
}
