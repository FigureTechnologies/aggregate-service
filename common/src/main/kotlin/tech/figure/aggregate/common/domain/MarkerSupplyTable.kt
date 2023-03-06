package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object MarkerSupplyTable: UUIDTable("marker_supply", columnName = "uuid") {
    val hash = text("hash")
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

open class MarkerSupplyEntityClass: UUIDEntityClass<MarkerSupplyRecord>(MarkerSupplyTable) {
    fun insert(
        hash: String,
        eventType: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        coins: String,
        denom: String,
        amount: String,
        administrator: String,
        toAddress: String,
        fromAddress: String,
        metadataBase: String,
        metadataDescription: String,
        metadataDisplay: String,
        metadataDenomUnits: String,
        metadataName: String,
        metadataSymbol: String
    ) {
        new(UUID.randomUUID()) {
            this.hash = hash
            this.eventType = eventType
            this.blockHeight = blockHeight
            this.blockTimestamp = blockTimestamp
            this.coins = coins
            this.denom = denom
            this.amount = amount
            this.administrator = administrator
            this.toAddress = toAddress
            this.fromAddress = fromAddress
            this.metadataBase = metadataBase
            this.metadataDescription = metadataDescription
            this.metadataDisplay = metadataDisplay
            this.metadataDenomUnits = metadataDenomUnits
            this.metadataName = metadataName
            this.metadataSymbol = metadataSymbol
        }
    }
}

class MarkerSupplyRecord(uuid: EntityID<UUID>): UUIDEntity(uuid) {

    companion object: MarkerSupplyEntityClass()

    var uuid by MarkerSupplyTable.id
    var hash by MarkerSupplyTable.hash
    var eventType by MarkerSupplyTable.eventType
    var blockHeight by MarkerSupplyTable.blockHeight
    var blockTimestamp by MarkerSupplyTable.blockTimestamp
    var coins by MarkerSupplyTable.coins
    var denom by MarkerSupplyTable.denom
    var amount by MarkerSupplyTable.amount
    var administrator by MarkerSupplyTable.administrator
    var toAddress by MarkerSupplyTable.toAddress
    var fromAddress by MarkerSupplyTable.fromAddress
    var metadataBase by MarkerSupplyTable.metadataBase
    var metadataDescription by MarkerSupplyTable.metadataDescription
    var metadataDisplay by MarkerSupplyTable.metadataDisplay
    var metadataDenomUnits by MarkerSupplyTable.metadataDenomUnits
    var metadataName by MarkerSupplyTable.metadataName
    var metadataSymbol by MarkerSupplyTable.metadataSymbol
}
