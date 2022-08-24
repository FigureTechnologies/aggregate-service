package tech.figure.aggregator.api.route.v1

import com.fasterxml.jackson.core.JsonProcessingException
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import tech.figure.aggregator.api.cache.CacheService
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import com.papsign.ktor.openapigen.route.throws
import tech.figure.aggregator.api.model.request.TxRequest
import tech.figure.aggregator.api.model.response.TxResponse
import tech.figure.aggregator.api.route.Tag
import tech.figure.aggregator.api.route.exception.OptionalResult
import tech.figure.aggregator.api.route.toOffsetDateTime
import io.ktor.http.HttpStatusCode

fun NormalOpenAPIRoute.feeRoute(cacheService: CacheService) {
    tag(Tag.Fee) {
        route("fee") {
            get<TxRequest, TxResponse>(
                info(
                    summary = "Get the total fee for a given address within a set date range"
                ),
                example = TxResponse.sampleFeeResponse
            ) { param ->
                if (param.address == "" || param.startDate == "" || param.endDate == "" || param.denom == "") {
                    throws(
                        HttpStatusCode.BadRequest.description("Invalid parameters"),
                        example = OptionalResult.FAIL,
                        exClass = JsonProcessingException::class
                    )
                }

                val response = cacheService.getFee(
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
