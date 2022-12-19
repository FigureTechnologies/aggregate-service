package tech.figure.aggregate.common.models.block

import com.squareup.moshi.JsonClass
import tech.figure.aggregate.common.models.fee.Fee
import tech.figure.aggregate.common.models.tx.TxEvent
import java.time.OffsetDateTime

/**
 * Used to represent block-level events like `reward`, `commission`, etc.
 */
@JsonClass(generateAdapter = true)
data class BlockTxData(
    val txHash: String,
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    val code: Long,
    val gasWanted: Long,
    val gasUsed: Long,
    val events: List<TxEvent>,
    val fee: Fee
)
