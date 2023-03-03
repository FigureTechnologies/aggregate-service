package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object FeesTable: UUIDTable("fees", columnName = "uuid") {
    val hash = text("hash")
    val txHash = text("tx_hash")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val fee = text("fee")
    val feeDenom = text("fee_denom")
    val sender = text("sender")
}

open class FeeEntityClass: UUIDEntityClass<FeeRecords>(FeesTable) {

    fun insert(
        hash: String,
        txHash: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        fee: String,
        feeDenom: String,
        sender: String
    ) = new(UUID.randomUUID()) {
            this.hash = hash
            this.txHash = txHash
            this.blockHeight = blockHeight
            this.blockTimestamp = blockTimestamp
            this.fee = fee
            this.feeDenom = feeDenom
            this.sender = sender
    }
}

class FeeRecords (uuid: EntityID<UUID>): UUIDEntity(uuid) {
    companion object: FeeEntityClass()

    var uuid by FeesTable.id
    var hash by FeesTable.hash
    var txHash by FeesTable.txHash
    var blockHeight by FeesTable.blockHeight
    var blockTimestamp by FeesTable.blockTimestamp
    var fee by FeesTable.fee
    var feeDenom by FeesTable.feeDenom
    var sender by FeesTable.sender
}
