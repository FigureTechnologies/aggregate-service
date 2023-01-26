package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.OffsetDateTime

object AttributesTable: IdTable<String>("attributes") {
    val hash = text("hash")
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val name = text("name")
    val value = text("value")
    val type = text("type")
    val account = text("account")
    val owner = text("owner")

    override val id = hash.entityId()
}

open class AttributesEntityClass: EntityClass<String, AttributesRecord>(AttributesTable) {

    private fun findByHash(hash: String) = find {  AttributesTable.id eq hash }.firstOrNull()

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
        findByHash(hash) ?: new(hash) {
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

class AttributesRecord(hash: EntityID<String>): Entity<String>(hash) {
    companion object: AttributesEntityClass()

    var hash by AttributesTable.id
    var eventType by AttributesTable.eventType
    var blockHeight by AttributesTable.blockHeight
    var blockTimestamp by AttributesTable.blockTimestamp
    var name by AttributesTable.name
    var value by AttributesTable.value
    var type by AttributesTable.type
    var account by AttributesTable.account
    var owner by AttributesTable.owner
}
