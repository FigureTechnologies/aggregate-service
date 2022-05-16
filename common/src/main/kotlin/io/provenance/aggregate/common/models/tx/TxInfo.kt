package io.provenance.aggregate.common.models.tx

import io.provenance.aggregate.common.models.Fee

data class TxInfo(
    val txHash: String? = "",
    val fee: Fee
)
