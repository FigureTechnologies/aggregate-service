package tech.figure.aggregate.common.db.model

import tech.figure.aggregate.common.db.model.impl.TxResponseData
import java.sql.Timestamp

data class TxMarkerSupply(
    val hash: String,
    val eventType: String?,
    val blockHeight: Long,
    val blockTimestamp: Timestamp,
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
) : TxResponseData
