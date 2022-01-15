package io.provenance.aggregate.common.models

import com.squareup.moshi.JsonClass
import java.time.OffsetDateTime

/**
 * Represents errored Tx events that collected a fee.
 */
@JsonClass(generateAdapter = true)
data class TxError (
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    val code: Long,
    val info: String,
    val fee: Int?,
    val feeDenom: String? = "nhash"
)
