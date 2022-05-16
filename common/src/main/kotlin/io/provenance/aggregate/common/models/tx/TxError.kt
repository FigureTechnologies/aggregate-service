package io.provenance.aggregate.common.models.tx

import com.squareup.moshi.JsonClass
import io.provenance.aggregate.common.models.Fee
import java.time.OffsetDateTime

/**
 * Represents errored Tx events that collected a fee.
 */
@JsonClass(generateAdapter = true)
data class TxError(
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    val code: Long,
    val info: String,
    val txHash: String,
    val fee: Fee,
)
