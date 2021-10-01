package io.provenance.aggregate.service.aws

import com.timgroup.statsd.StatsDClient
import io.provenance.aggregate.service.DynamoConfig
import io.provenance.aggregate.service.S3Config

class DefaultAwsInterface(s3Config: S3Config, dynamoConfig: DynamoConfig, dogStatsClient: StatsDClient)
    : AwsInterface(s3Config, dynamoConfig, dogStatsClient)
