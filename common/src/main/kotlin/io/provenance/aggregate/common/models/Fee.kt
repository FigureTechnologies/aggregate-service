package io.provenance.aggregate.common.models

data class Fee(
    val fee: Long? = 0L,
    val denom: String? = "",
    val signerAddr: List<String>? = mutableListOf(),
    val IncurringAddr: String? = ""
)