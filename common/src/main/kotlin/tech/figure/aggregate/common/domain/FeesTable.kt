package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.OffsetDateTime

object FeesTable: IdTable<String>("fees") {
    val hash = text("hash")
    val txHash = text("tx_hash")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val fee = text("fee")
    val feeDenom = text("fee_denom")
    val sender = text("sender")

    override val id = hash.entityId()
}

open class FeeEntityClass: EntityClass<String, FeeRecords>(FeesTable) {

    private fun findByHash(hash: String) = find { FeesTable.id eq hash }.firstOrNull()

    fun insert(
        hash: String,
        txHash: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        fee: String,
        feeDenom: String,
        sender: String
    ) = findByHash(hash) ?: new(hash) {
        this.txHash = txHash
        this.blockHeight = blockHeight
        this.blockTimestamp = blockTimestamp
        this.fee = fee
        this.feeDenom = feeDenom
        this.sender = sender
    }

}

class FeeRecords (hash: EntityID<String>): Entity<String>(hash) {
    companion object: FeeEntityClass()

    var hash by FeesTable.id
    var txHash by FeesTable.txHash
    var blockHeight by FeesTable.blockHeight
    var blockTimestamp by FeesTable.blockTimestamp
    var fee by FeesTable.fee
    var feeDenom by FeesTable.feeDenom
    var sender by FeesTable.sender
}
