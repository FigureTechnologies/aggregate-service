package io.provenance.aggregate.common.aws

import io.provenance.aggregate.common.S3Config

/**
 * The default client provider to use when interacting with AWS.
 *
 * @property s3Client The AWS SDK S3 async client to use.
 * @property bucket The S3 bucket to write results to.
 */
class DefaultAwsClient(s3Config: S3Config) : AwsClient(s3Config)
