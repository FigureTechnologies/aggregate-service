package io.provenance.aggregate.repository.model


data class TxEvents(
    val txHash: String?,
    val blockHeight: Long?,
    val eventType: String?,
    val attributes: List<EventData>?,
)


data class EventData(val key: String?, val value: String?, val index: Boolean)