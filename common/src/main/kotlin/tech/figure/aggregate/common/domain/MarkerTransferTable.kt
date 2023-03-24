package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.OffsetDateTime

object MarkerTransferTable: IdTable<String>("marker_transfer") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val amount = text("amount")
    val denom = text("denom")
    val administrator = text("administrator")
    val toAddress = text("to_address")
    val fromAddress = text("from_address")

    override val id = hash.entityId()
}

open class MarkerTransferEntityClass: EntityClass<String, MarkerTransferRecord>(MarkerTransferTable) {

    private fun findByHash(hash: String) = find {  MarkerTransferTable.id eq hash }.firstOrNull()

    fun insert(
        hash: String,
        eventType: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        amount: String,
        denom: String,
        administrator: String,
        toAddress: String,
        fromAddress: String
    ) {
        findByHash(hash) ?: new(hash) {
            this.eventType = eventType
            this.blockHeight = blockHeight
            this.blockTimestamp = blockTimestamp
            this.amount = amount
            this.denom = denom
            this.administrator = administrator
            this.toAddress = toAddress
            this.fromAddress = fromAddress
        }
    }
}

class MarkerTransferRecord(hash: EntityID<String>): Entity<String>(hash) {
    companion object: MarkerTransferEntityClass()

    var hash by MarkerTransferTable.id
    var eventType by MarkerTransferTable.eventType
    var blockHeight by MarkerTransferTable.blockHeight
    var blockTimestamp by MarkerTransferTable.blockTimestamp
    var amount by MarkerTransferTable.amount
    var denom by MarkerTransferTable.denom
    var administrator by MarkerTransferTable.administrator
    var toAddress by MarkerTransferTable.toAddress
    var fromAddress by MarkerTransferTable.fromAddress
}
