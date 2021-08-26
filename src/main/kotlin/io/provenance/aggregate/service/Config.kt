package io.provenance.aggregate.service

data class Config_Stream(
    val websocket_uri: String,
    val rpc_uri: String,
    val batch_size: Int,
    val throttle_duration_ms: Long = 0
)

data class Config_Event(
    val stream: Config_Stream
)

data class Config(
    val environment: String,
    val event: Config_Event
)