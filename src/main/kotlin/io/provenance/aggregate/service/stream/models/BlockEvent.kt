package io.provenance.aggregate.service.stream.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BlockEvent(
    val height: Long,
    val eventType: String,
    val attributes: List<Event>,
)