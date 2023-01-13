package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.id.UUIDTable

object MarkerTransferTable: UUIDTable("marker_transfer", columnName = "id") {
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val amount = text("amount")
    val denom = text("denom")
    val administrator = text("administrator")
    val toAddress = text("to_address")
    val fromAddress = text("from_address")
}
