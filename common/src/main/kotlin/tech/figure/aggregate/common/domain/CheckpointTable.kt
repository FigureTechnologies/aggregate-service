package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

object CheckpointTable: IdTable<String>("checkpoint") {
    val name = text("name").entityId()
    val blockHeight = long("block_height")
    override val id = name
}

private val log = LoggerFactory.getLogger("db")


object Checkpoint {
    fun findLastKnownBlockHeight(): ResultRow? {
        val record = CheckpointTable.select { CheckpointTable.name eq "last_known" }.firstOrNull()
        log.info("found record:$record")
        if (record == null) {
            log.info("no rec found!")
            return null
        }
        return record
    }

    fun upsert(height: Long) {
        val record = findLastKnownBlockHeight()
        if (record == null) {
            CheckpointTable.insert {
                it[name] = "last_known"
                it[blockHeight] = height
            }.also {
                log.info("Checkpoint::creating new record at height: $height")
            }
        } else {
            log.info("Checkpoint::Updating max block height to = $height")
            record[CheckpointTable.blockHeight] = height
            CheckpointTable.update({ CheckpointTable.name eq "last_known" }) {
                it[blockHeight] = height
            }
        }
    }
}
