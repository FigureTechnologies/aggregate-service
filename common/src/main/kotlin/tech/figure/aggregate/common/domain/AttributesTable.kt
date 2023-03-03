package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object AttributesTable: UUIDTable("attributes", columnName = "uuid") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val name = text("name")
    val value = text("value")
    val type = text("type")
    val account = text("account")
    val owner = text("owner")
}

open class AttributesEntityClass: UUIDEntityClass<AttributesRecord>(AttributesTable) {

    fun insert(
        hash: String,
        eventType: String,
        blockHeight: Double,
        blockTimestamp: OffsetDateTime,
        name: String,
        value: String,
        type: String,
        account: String,
        owner: String
    ) {
        new(UUID.randomUUID()) {
            this.hash = hash
            this.eventType = eventType
            this.blockHeight = blockHeight
            this.blockTimestamp = blockTimestamp
            this.name = name
            this.value = value
            this.type = type
            this.account = account
            this.owner = owner
        }
    }
}

class AttributesRecord(uuid: EntityID<UUID>): UUIDEntity(uuid) {
    companion object: AttributesEntityClass()

    var uuid by AttributesTable.id
    var hash by AttributesTable.hash
    var eventType by AttributesTable.eventType
    var blockHeight by AttributesTable.blockHeight
    var blockTimestamp by AttributesTable.blockTimestamp
    var name by AttributesTable.name
    var value by AttributesTable.value
    var type by AttributesTable.type
    var account by AttributesTable.account
    var owner by AttributesTable.owner
}
