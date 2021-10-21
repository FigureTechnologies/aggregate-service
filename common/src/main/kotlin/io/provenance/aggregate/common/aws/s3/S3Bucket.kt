package io.provenance.aggregate.common.aws.s3

/**
 * A value class wrapper around a string that names an AWS S3 bucket.
 *
 * @property name The name of the S3 bucket.
 */
@JvmInline
value class S3Bucket(val name: String)
