package io.provenance.aggregate.service.stream.batch

import java.util.UUID

@JvmInline
value class BatchId(val value: String = "${UUID.randomUUID().toString()}") {
    override fun toString() = value
}