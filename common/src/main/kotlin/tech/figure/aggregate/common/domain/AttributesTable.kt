package tech.figure.aggregate.common.domain

import org.jetbrains.exposed.dao.id.UUIDTable

object AttributesTable: UUIDTable("attributes", columnName = "id") {
    val eventType = text("event_type")
    val blockHeight = double("block_height")
    val blockTimestamp = offsetDatetime("block_timestamp")
    val name = text("name")
    val value = text("value")
    val type = text("type")
    val account = text("account")
    val owner = text("owner")
}
