package tech.figure.aggregate.service.stream.extractors.model

import kotlinx.serialization.Serializable

@Serializable
data class CoinTransfer(
    val eventType: String?,
    val blockHeight: Long,
    val blockTimestamp: String?,
    val txHash: String,
    val recipient: String?,
    val sender: String?,
    val amount: String,
    val denom: String
)
