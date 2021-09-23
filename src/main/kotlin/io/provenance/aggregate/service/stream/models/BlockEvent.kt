package io.provenance.aggregate.service.stream.models

import com.squareup.moshi.JsonClass

/**
 * Used to represent block-level events like "reward", "commission", etc.
 */
@JsonClass(generateAdapter = true)
data class BlockEvent(
    val height: Long,
    override val eventType: String,
    override val attributes: List<Event>
) : EncodedBlockchainEvent