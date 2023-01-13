package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.id.UUIDTable

object FeesTable: UUIDTable("fees", columnName = "id") {
    val txHash = text("tx_hash")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val fee = text("fee")
    val feeDenom = text("fee_denom")
    val sender = text("sender")
}
