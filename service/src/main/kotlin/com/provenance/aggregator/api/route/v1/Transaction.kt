package com.provenance.aggregator.api.route.v1

import com.fasterxml.jackson.core.JsonProcessingException
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import com.papsign.ktor.openapigen.route.throws
import com.provenance.aggregator.api.cache.CacheService
import com.provenance.aggregator.api.model.request.TxRequest
import com.provenance.aggregator.api.model.response.TxResponse
import com.provenance.aggregator.api.route.Tag
import com.provenance.aggregator.api.route.exception.OptionalResult
import com.provenance.aggregator.api.route.toOffsetDateTime
import io.ktor.http.HttpStatusCode

fun NormalOpenAPIRoute.txRoute(cacheService: CacheService){
    tag(Tag.Transaction) {
        route("transaction") {
            get<TxRequest, TxResponse>(
                info(
                    summary = "Get the net denom transaction for a given address within a set date range"
                ),
                example = TxResponse.sampleTxResponse
            ) { param ->
                if (param.address == "" || param.startDate == "" || param.endDate == "" || param.denom == "") {
                    throws(
                        HttpStatusCode.BadRequest.description("Invalid parameters"),
                        example = OptionalResult.FAIL,
                        exClass = JsonProcessingException::class
                    )
                }
                val response = cacheService.getTx(
                    param.address,
                    param.startDate.toOffsetDateTime(),
                    param.endDate.toOffsetDateTime(),
                    param.denom
                )
                respond(response)
            }
        }
    }
}
