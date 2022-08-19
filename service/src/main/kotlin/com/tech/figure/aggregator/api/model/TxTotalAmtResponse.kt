package com.tech.figure.aggregator.api.model

import com.papsign.ktor.openapigen.annotations.Response

@Response("Address data found", statusCode = 200)
data class TxTotalAmtResponse(
    val addr: String,
    val startDate: String,
    val endDate: String,
    val total: Long,
    val denom: String
)
