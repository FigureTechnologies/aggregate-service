package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigAlias
import io.provenance.aggregate.service.aws.dynamodb.DynamoTable
import io.provenance.aggregate.service.aws.s3.S3Bucket

// Data classes in this file are intended to be instantiated by the hoplite configuration library

data class WebsocketStreamConfig(
    val uri: String,
    @ConfigAlias("throttle_duration_ms") val throttleDurationMs: Long = 0
)

data class RpcStreamConfig(val uri: String)

data class StreamEventsFilterConfig(
    @ConfigAlias("tx_events") val txEvents: Set<String> = emptySet(),
    @ConfigAlias("block_events") val blockEvents: Set<String> = emptySet()
) {
    companion object {
        fun empty() = StreamEventsFilterConfig()
    }
}

data class BatchConfig(
    val size: Int,
    @ConfigAlias("timeout_ms") val timeoutMillis: Long?,
)

data class EventStreamConfig(
    val websocket: WebsocketStreamConfig,
    val rpc: RpcStreamConfig,
    val batch: BatchConfig,
    val filter: StreamEventsFilterConfig = StreamEventsFilterConfig.empty()
)

data class S3Config(
    val bucket: S3Bucket
)

data class DynamoConfig(
    val region: String?,
    @ConfigAlias("service_metadata_table") val serviceMetadataTable: DynamoTable,
    @ConfigAlias("block_batch_table") val blockBatchTable: DynamoTable,
    @ConfigAlias("block_metadata_table") val blockMetadataTable: DynamoTable
)

data class AwsConfig(
    val region: String?,
    val s3: S3Config,
    val dynamodb: DynamoConfig
)

data class UploadConfig(
    val extractors: List<String> = emptyList()
) {
    companion object {
        fun empty() = UploadConfig()
    }
}

data class Config(
    val aws: AwsConfig,
    @ConfigAlias("event-stream") val eventStream: EventStreamConfig,
    val upload: UploadConfig = UploadConfig.empty()
)