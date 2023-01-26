package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.OffsetDateTime

object CoinTransferTable: IdTable<String>("coin_transfer") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val txhash = text("tx_hash")
    val recipient = text("recipient")
    val sender = text("sender")
    val amount = text("amount")
    val denom = text("denom")

    override val id = hash.entityId()
}

open class CoinTransferEntityClass: EntityClass<String, CoinTransferRecord>(CoinTransferTable) {

    private fun findByHash(hash: String) = find {  CoinTransferTable.id eq hash }.firstOrNull()

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
        findByHash(hash) ?: new(hash) {
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

class CoinTransferRecord(hash: EntityID<String>): Entity<String>(hash) {
    companion object: CoinTransferEntityClass()

    var hash by CoinTransferTable.id
    var eventType by CoinTransferTable.eventType
    var blockHeight by CoinTransferTable.blockHeight
    var blockTimestamp by CoinTransferTable.blockTimestamp
    var txHash by CoinTransferTable.txhash
    var recipient by CoinTransferTable.recipient
    var sender by CoinTransferTable.sender
    var amount by CoinTransferTable.amount
    var denom by CoinTransferTable.denom
}




