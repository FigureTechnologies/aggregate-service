package io.provenance.aggregate.common.models

data class Fee(
    val fee: Long? = 0L,
    val denom: String? = "",
    val signerAddrs: List<String>? = mutableListOf(),
    val incurrAddr: String? = ""
)
