package io.provenance.aggregate.service.stream.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TxEvent(
    val height: Long,
    val txHash: String,
    val eventType: String,
    val attributes: List<Event>,
)