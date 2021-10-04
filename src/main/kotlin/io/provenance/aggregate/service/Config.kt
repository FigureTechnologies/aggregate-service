package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigAlias
import io.provenance.aggregate.service.aws.dynamodb.DynamoTable
import io.provenance.aggregate.service.aws.s3.S3Bucket

data class ConfigStream(
    @ConfigAlias("websocket_uri") val websocketUri: String,
    @ConfigAlias("rpc_uri") val rpcUri: String,
    @ConfigAlias("batch_size") val batchSize: Int,
    @ConfigAlias("throttle_duration_ms") val throttleDurationMs: Long = 0
)

data class S3Config(
    val region: String?,
    val bucket: S3Bucket
)

data class DynamoConfig(
    val region: String?,
    @ConfigAlias("service_metadata_table") val serviceMetadataTable: DynamoTable,
    @ConfigAlias("block_batch_table") val blockBatchTable: DynamoTable,
    @ConfigAlias("block_metadata_table") val blockMetadataTable: DynamoTable
)

data class EventConfig(
    val stream: ConfigStream
)

data class Config(
    val s3: S3Config,
    val dynamodb: DynamoConfig,
    val event: EventConfig
)