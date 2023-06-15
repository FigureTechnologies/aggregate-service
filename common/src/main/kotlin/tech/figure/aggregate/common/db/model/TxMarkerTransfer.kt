package tech.figure.aggregate.common.db.model

import java.sql.Timestamp

data class TxMarkerTransfer(
    val hash: String,
    val eventType: String,
    val blockHeight: Long,
    val blockTimestamp: Timestamp,
    val amount: String,
    val denom: String,
    val administrator: String,
    val toAddress: String,
    val fromAddress: String
)
