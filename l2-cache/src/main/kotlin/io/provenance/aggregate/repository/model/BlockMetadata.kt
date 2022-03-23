package io.provenance.aggregate.repository.model

data class BlockMetadata(
    val blockHeight: Long?,
    val txHash: List<String>?,
    val timestamp: String?,
    val numTxs: Long?
)
