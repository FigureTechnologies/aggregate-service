package io.provenance.aggregate.service.aws

import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.S3Config

/**
 * The default client provider to use when interacting with AWS.
 *
 * @property s3Client The AWS SDK S3 async client to use.
 * @property bucket The S3 bucket to write results to.
 */
class DefaultAwsClient(s3Config: S3Config, dynamoConfig: DynamoConfig) : AwsClient(s3Config, dynamoConfig)