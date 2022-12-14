package tech.figure.aggregate.common.models.block

import com.squareup.moshi.JsonClass
import tech.figure.aggregate.common.models.tx.TxEvent
import tech.figure.block.api.proto.BlockOuterClass
import java.time.OffsetDateTime

@JsonClass(generateAdapter = true)
data class StreamBlock(
    val block: BlockOuterClass.Block,
    val blockTxData: List<BlockTxData>,
    val height: Long? = block.height,
    val blockDateTime: OffsetDateTime
)
