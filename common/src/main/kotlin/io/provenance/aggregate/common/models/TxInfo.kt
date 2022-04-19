package io.provenance.aggregate.common.models

data class TxInfo(
    val txHash: String? = "",
    val fee: Pair<Long?, String?>? = null
)
