package io.provenance.aggregate.service.aws

import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.S3Config

class DefaultAwsInterface(s3Config: S3Config, dynamoConfig: DynamoConfig) : AwsInterface(s3Config, dynamoConfig)