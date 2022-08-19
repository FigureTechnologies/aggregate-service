package com.tech.figure.aggregator.api.model.request

import com.papsign.ktor.openapigen.annotations.Path
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam

@Path("{address}")
data class TxRequest(
    @PathParam("transaction's address") val address: String,
    @QueryParam("start date") val startDate: String,
    @QueryParam("end date") val endDate: String,
    @QueryParam("denom type") val denom: String
)
