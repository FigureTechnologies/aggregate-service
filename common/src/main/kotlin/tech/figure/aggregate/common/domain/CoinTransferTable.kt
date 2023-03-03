package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object CoinTransferTable: UUIDTable("coin_transfer", columnName = "uuid") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val txhash = text("tx_hash")
    val recipient = text("recipient")
    val sender = text("sender")
    val amount = text("amount")
    val denom = text("denom")
}

open class CoinTransferEntityClass: UUIDEntityClass<CoinTransferRecord>(CoinTransferTable) {

    fun insert(
        hash: String,
        eventType: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        txHash: String,
        recipient: String,
        sender: String,
        amount: String,
        denom: String
    ) {
        new(UUID.randomUUID()) {
            this.hash = hash
            this.eventType = eventType
            this.blockHeight = blockHeight
            this.blockTimestamp = blockTimestamp
            this.txHash = txHash
            this.recipient = recipient
            this.sender = sender
            this.amount = amount
            this.denom = denom
        }
    }
}

class CoinTransferRecord(uuid: EntityID<UUID>): UUIDEntity(uuid) {
    companion object: CoinTransferEntityClass()

    var uuid by CoinTransferTable.id
    var hash by CoinTransferTable.hash
    var eventType by CoinTransferTable.eventType
    var blockHeight by CoinTransferTable.blockHeight
    var blockTimestamp by CoinTransferTable.blockTimestamp
    var txHash by CoinTransferTable.txhash
    var recipient by CoinTransferTable.recipient
    var sender by CoinTransferTable.sender
    var amount by CoinTransferTable.amount
    var denom by CoinTransferTable.denom
}




