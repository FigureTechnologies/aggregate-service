package tech.figure.aggregate.common.models.block

import com.squareup.moshi.JsonClass
import io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.provenance.eventstream.stream.models.Block
import tech.figure.aggregate.common.models.tx.TxEvent

interface StreamBlock {
    val block: Block
    val blockEvents: List<BlockEvent>
    val blockResult: List<BlockResultsResponseResultTxsResults>?
    val txEvents: List<TxEvent>
    val historical: Boolean
    val height: Long? get() = block.header?.height
}

/**
 * Wraps a block and associated block-level and transaction-level events, as well as a marker to determine if the
 * block is "historical" (not live streamed), and metadata, if any.
 */
@JsonClass(generateAdapter = true)
data class StreamBlockImpl(
    override val block: Block,
    override val blockEvents: List<BlockEvent>,
    override val blockResult: List<BlockResultsResponseResultTxsResults>?,
    override val txEvents: List<TxEvent>,
    override val historical: Boolean = false
) : StreamBlock
