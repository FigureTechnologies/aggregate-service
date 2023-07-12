package tech.figure.aggregate.common.models.stream

import kotlinx.serialization.Serializable
import tech.figure.aggregate.common.models.stream.impl.StreamTypeImpl

@Serializable
data class MarkerTransfer(
    val eventType: String,
    val blockHeight: Long,
    val blockTimestamp: String,
    val amount: String,
    val denom: String,
    val administrator: String,
    val toAddress: String,
    val fromAddress: String
): StreamTypeImpl
