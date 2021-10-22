package io.provenance.aggregate.common.models

import java.util.UUID

/**
 * A value class wrapping a string representing a batch ID.
 *
 * @property value A batch ID.
 */
@JvmInline
value class BatchId(val value: String = "${UUID.randomUUID().toString()}") {
    override fun toString() = value
}
