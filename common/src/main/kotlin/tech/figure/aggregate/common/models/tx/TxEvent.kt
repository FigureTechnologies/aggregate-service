package tech.figure.aggregate.common.models.tx

import com.squareup.moshi.JsonClass
import tech.figure.aggregate.common.models.Event
import tech.figure.aggregate.common.models.block.EncodedBlockchainEvent
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
    override val eventType: String?,
    override val attributes: List<Event>
) : EncodedBlockchainEvent
