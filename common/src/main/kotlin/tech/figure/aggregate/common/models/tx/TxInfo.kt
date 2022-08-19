package tech.figure.aggregate.common.models.tx

import tech.figure.aggregate.common.models.Fee

data class TxInfo(
    val txHash: String? = "",
    val fee: Fee
)
