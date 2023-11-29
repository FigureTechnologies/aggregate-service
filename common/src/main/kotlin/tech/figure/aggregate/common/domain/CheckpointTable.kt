package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import tech.figure.aggregate.common.logger

object CheckpointTable: IdTable<String>("checkpoint") {
    val name = text("name").entityId()
    val blockHeight = long("block_height")
    override val id = name
}

open class CheckpointEntityClass: EntityClass<String, CheckpointRecord>(CheckpointTable) {

    private val log = logger()

    fun getIt(): ResultRow? {
        val record = CheckpointTable.select { CheckpointTable.name eq "last_known" }.firstOrNull()
        log.info("found record:$record")
        if (record == null) {
            log.info("no rec found!")
            return null
        }
        return record
    }

    fun findLastKnownBlockHeight() = getIt()

    fun upsert(height: Long) {
        val record = findLastKnownBlockHeight()
        if (record == null) {
            new("last_known") {
                this.blockHeight = height
            }.also {
                log.info("Checkpoint::creating new record at height: $height")
            }
        } else {
            log.info("Checkpoint::Updating max block height to = $height")
            record[CheckpointTable.blockHeight] = height
            CheckpointTable.update({ CheckpointTable.name eq "last_known" }) {
                it[blockHeight]= height
            }
        }
    }
}

data class CheckpointRecord(val name: EntityID<String>): Entity<String>(name) {

    companion object: CheckpointEntityClass()

    var blockHeight by CheckpointTable.blockHeight

}
