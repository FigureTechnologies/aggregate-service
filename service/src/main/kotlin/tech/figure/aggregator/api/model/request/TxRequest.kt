package tech.figure.aggregator.api.model.request

import com.papsign.ktor.openapigen.annotations.Path
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam

@Path("{address}")
data class TxRequest(
    @PathParam("transaction's address") val address: String,
    @QueryParam("start date - yyyy-mm-dd") val startDate: String,
    @QueryParam("end date - yyyy-mm-dd") val endDate: String,
    @QueryParam("denom type") val denom: String,
    @QueryParam("limit") val limit: String?,
    @QueryParam("offset") val offset: String?
)
