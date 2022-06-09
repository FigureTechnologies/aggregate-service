package io.provenance.aggregate.repository.model.l2cache

data class BlockMetadata(
    val blockHeight: Long?,
    val txHash: List<String>,
    val timestamp: String?,
    val numTxs: Long?
)
