package com.provenance.aggregator.api.com.provenance.aggregator.api.model

import java.time.OffsetDateTime

data class TxDailyTotal(
    val addr: String,
    val date: String,
    val total: Long,
    val denom: String = "nhash"
)
