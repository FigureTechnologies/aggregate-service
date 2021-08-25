package io.provenance.aggregate.service.stream

import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.BlockResultsResponseResultEvents
import io.provenance.aggregate.service.stream.extensions.*
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NewBlockResult(
    val query: String?,
    val data: NewBlockEventResultData
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultData(
    val type: String,
    val value: NewBlockEventResultValue
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultBeginBlock(
    val events: List<BlockResultsResponseResultEvents>
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultValue(
    val block: Block,
    val result_begin_block: NewBlockEventResultBeginBlock
)

@JsonClass(generateAdapter = true)
data class Subscribe(
    val jsonrpc: String = "2.0",
    val id: String = "0",
    val method: String = "subscribe",
    val params: SubscribeParams
) {
    constructor(query: String) : this(params = SubscribeParams(query))
}

@JsonClass(generateAdapter = true)
open class RpcRequest(val method: String, val params: Any? = null) {
    open val jsonrpc: String = "2.0"
    open val id: String = "0"
}

@JsonClass(generateAdapter = true)
data class RpcResponse<T>(
    val jsonrpc: String,
    val id: String,
    val result: T? = null,
    val error: RpcError? = null
)

@JsonClass(generateAdapter = true)
data class RpcError(
    val code: Int,
    val message: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class SubscribeParams(
    val query: String
)
