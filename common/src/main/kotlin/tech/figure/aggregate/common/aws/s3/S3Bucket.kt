package tech.figure.aggregate.common.aws.s3

/**
 * A value class wrapper around a string that names an AWS S3 bucket.
 *
 * @property value The name of the S3 bucket.
 */
@JvmInline
value class S3Bucket(val value: String) {
    override fun toString(): String = value
}
