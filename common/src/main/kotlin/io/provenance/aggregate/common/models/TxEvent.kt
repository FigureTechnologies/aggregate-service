package io.provenance.aggregate.common.models

import com.squareup.moshi.JsonClass
import java.time.OffsetDateTime

/**
 * Used to represent transaction-level events like "transfer", "message", metadata events
 * (`provenance.metadata.v1.EventScopeCreated`), etc.
 */
@JsonClass(generateAdapter = true)
data class TxEvent(
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    val txHash: String,
    val fee: Long?,
    val feeDenom: String? = "nhash",
    override val eventType: String,
    override val attributes: List<Event>
) : EncodedBlockchainEvent
