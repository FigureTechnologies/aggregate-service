package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import tech.figure.aggregate.common.logger

object CheckpointTable: IdTable<String>("checkpoint") {
    val name = text("name")
    val blockHeight = long("block_height")
    override val id = name.entityId()
}

open class CheckpointEntityClass: EntityClass<String, CheckpointRecord>(CheckpointTable) {

    private val log = logger()

    fun findLastKnownBlockHeight() = find { CheckpointTable.id eq "last_known" }.firstOrNull()

    fun upsert(height: Long) {
        val record = findLastKnownBlockHeight()
        if(record == null) {
            new("last_known") {
                this.blockHeight = height
            }
        } else {
            log.info("Checkpoint::Updating max block height to = $height")
            record.blockHeight = height
        }
    }
}

class CheckpointRecord(name: EntityID<String>): Entity<String>(name) {

    companion object: CheckpointEntityClass()

    var name by CheckpointTable.name
    var blockHeight by CheckpointTable.blockHeight

}
