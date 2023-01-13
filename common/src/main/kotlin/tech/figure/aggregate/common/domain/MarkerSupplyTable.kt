package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.id.UUIDTable

object MarkerSupplyTable: UUIDTable("marker_supply", columnName = "id") {
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val coins = text("coins")
    val denom = text("denom")
    val amount = text("amount")
    val administrator = text("administrator")
    val toAddress = text("to_address")
    val fromAddress = text("from_address")
    val metadataBase = text("metadata_base")
    val metadataDescription = text("metadata_description")
    val metadataDisplay = text("metadata_display")
    val metadataDenomUnits = text("metadata_denom_units")
    val metadataName = text("metadata_name")
    val metadataSymbol = text("metadata_symbol")
}
