package tech.figure.aggregate.common.models.stream

import kotlinx.serialization.Serializable
import tech.figure.aggregate.common.models.stream.impl.StreamTypeImpl

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
): StreamTypeImpl

