package io.provenance.aggregate.service.stream.models.rpc.request

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Subscribe(
    val jsonrpc: String = "2.0",
    val id: String = "0",
    val method: String = "subscribe",
    val params: Params
) {
    @JsonClass(generateAdapter = true)
    data class Params(
        val query: String
    )

    constructor(query: String) : this(params = Params(query))
}