package io.provenance.aggregate.common.models

import com.squareup.moshi.JsonClass

interface StreamBlock {
    val block: io.provenance.eventstream.stream.models.Block
    val blockEvents: List<BlockEvent>
    val blockResult: List<io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults>?
    val txEvents: List<TxEvent>
    val txErrors: List<TxError>
    val historical: Boolean
    val height: Long? get() = block.header?.height
}

/**
 * Wraps a block and associated block-level and transaction-level events, as well as a marker to determine if the
 * block is "historical" (not live streamed), and metadata, if any.
 */
@JsonClass(generateAdapter = true)
data class StreamBlockImpl(
    override val block: io.provenance.eventstream.stream.models.Block,
    override val blockEvents: List<BlockEvent>,
    override val blockResult: List<io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults>?,
    override val txEvents: List<TxEvent>,
    override val txErrors: List<TxError>,
    override val historical: Boolean = false
) : StreamBlock
