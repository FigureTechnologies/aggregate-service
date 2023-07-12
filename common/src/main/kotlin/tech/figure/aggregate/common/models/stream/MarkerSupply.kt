package tech.figure.aggregate.common.models.stream

import kotlinx.serialization.Serializable
import tech.figure.aggregate.common.models.stream.impl.StreamTypeImpl

@Serializable
data class MarkerSupply(
    val eventType: String?,
    val blockHeight: Long,
    val blockTimestamp: String?,
    val coins: String?,
    val denom: String?,
    val amount: String?,
    val administrator: String?,
    val toAddress: String?,
    val fromAddress: String?,
    val metadataBase: String?,
    val metadataDescription: String?,
    val metadataDisplay: String?,
    val metadataDenomUnits: String?,
    val metadataName: String?,
    val metadataSymbol: String?
): StreamTypeImpl

