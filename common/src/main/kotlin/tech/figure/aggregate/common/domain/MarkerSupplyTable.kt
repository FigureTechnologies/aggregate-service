package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.OffsetDateTime

object MarkerSupplyTable: IdTable<String>("marker_supply") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val txHash = text("tx_hash")
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

    override val id = hash.entityId()
}

open class MarkerSupplyEntityClass: EntityClass<String, MarkerSupplyRecord>(MarkerSupplyTable) {

    private fun findByHash(hash: String) = find { MarkerSupplyTable.id eq hash }.firstOrNull()

    fun insert(
        hash: String,
        eventType: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        txHash: String,
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
        findByHash(hash) ?: new(hash) {
            this.eventType = eventType
            this.blockHeight = blockHeight
            this.blockTimestamp = blockTimestamp
            this.txHash = txHash
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

class MarkerSupplyRecord(hash: EntityID<String>): Entity<String>(hash) {

    companion object: MarkerSupplyEntityClass()

    var hash by MarkerSupplyTable.id
    var eventType by MarkerSupplyTable.eventType
    var blockHeight by MarkerSupplyTable.blockHeight
    var blockTimestamp by MarkerSupplyTable.blockTimestamp
    var txHash by MarkerSupplyTable.txHash
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
