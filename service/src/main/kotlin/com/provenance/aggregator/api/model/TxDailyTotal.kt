package com.provenance.aggregator.api.model

data class TxDailyTotal(
    val addr: String,
    val date: String,
    val total: Long,
    val denom: String
)
