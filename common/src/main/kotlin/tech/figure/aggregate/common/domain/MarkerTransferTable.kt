package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object MarkerTransferTable: UUIDTable("marker_transfer", columnName = "uuid") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val amount = text("amount")
    val denom = text("denom")
    val administrator = text("administrator")
    val toAddress = text("to_address")
    val fromAddress = text("from_address")
}

open class MarkerTransferEntityClass: UUIDEntityClass<MarkerTransferRecord>(MarkerTransferTable) {
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
        new(UUID.randomUUID()) {
            this.hash = hash
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

class MarkerTransferRecord(uuid: EntityID<UUID>): UUIDEntity(uuid) {
    companion object: MarkerTransferEntityClass()

    var uuid by MarkerTransferTable.id
    var hash by MarkerTransferTable.hash
    var eventType by MarkerTransferTable.eventType
    var blockHeight by MarkerTransferTable.blockHeight
    var blockTimestamp by MarkerTransferTable.blockTimestamp
    var amount by MarkerTransferTable.amount
    var denom by MarkerTransferTable.denom
    var administrator by MarkerTransferTable.administrator
    var toAddress by MarkerTransferTable.toAddress
    var fromAddress by MarkerTransferTable.fromAddress
}
