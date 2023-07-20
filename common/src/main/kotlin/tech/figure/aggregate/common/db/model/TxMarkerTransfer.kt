package tech.figure.aggregate.common.db.model

import tech.figure.aggregate.common.db.model.impl.TxResponseData
import java.sql.Timestamp

data class TxMarkerTransfer(
    val hash: String,
    val eventType: String,
    val blockHeight: Long,
    val blockTimestamp: Timestamp,
    val txHash: String,
    val amount: String,
    val denom: String,
    val administrator: String,
    val toAddress: String,
    val fromAddress: String
) : TxResponseData
