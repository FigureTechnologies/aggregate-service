package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.id.UUIDTable

object CoinTransferTable: UUIDTable("coin_transfer", columnName = "id") {
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val txhash = text("tx_hash")
    val recipient = text("recipient")
    val sender = text("sender")
    val amount = text("amount")
    val denom = text("denom")
}



