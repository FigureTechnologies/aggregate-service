package io.provenance.aggregate.service.stream.models

import com.squareup.moshi.JsonClass

/**
 * Used to represent transaction-level events like "transfer", "message", metadata events
 * (provenance.metadata.v1.EventScopeCreated, etc.), etc.
 */
@JsonClass(generateAdapter = true)
data class TxEvent(
    val height: Long,
    val txHash: String,
    override val eventType: String,
    override val attributes: List<Event>,
) : EncodedBlockchainEvent