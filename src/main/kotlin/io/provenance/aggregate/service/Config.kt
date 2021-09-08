package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigAlias

data class ConfigStream(
    @ConfigAlias("websocket_uri") val websocketUri: String,
    @ConfigAlias("rpc_uri") val rpcUri: String,
    @ConfigAlias("batch_size") val batchSize: Int,
    @ConfigAlias("throttle_duration_ms") val throttleDurationMs: Long = 0
)

data class S3Config(
    val region: String?,
    val bucket: String
)

data class EventConfig(
    val stream: ConfigStream
)

data class Config(
    val environment: Environment,
    val s3: S3Config,
    val event: EventConfig
)