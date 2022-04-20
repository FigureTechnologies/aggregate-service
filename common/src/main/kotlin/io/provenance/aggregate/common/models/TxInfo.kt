package io.provenance.aggregate.common.models

data class TxInfo(
    val txHash: String? = "",
    val fee: Fee
)
