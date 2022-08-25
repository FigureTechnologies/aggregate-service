package tech.figure.aggregator.api.route.v1

import com.fasterxml.jackson.core.JsonProcessingException
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import com.papsign.ktor.openapigen.route.throws
import tech.figure.aggregator.api.model.TxCoinTransferData
import tech.figure.aggregator.api.cache.CacheService
import tech.figure.aggregator.api.model.request.TxRequest
import tech.figure.aggregator.api.model.response.TxResponse
import tech.figure.aggregator.api.route.Tag
import tech.figure.aggregator.api.route.exception.OptionalResult
import tech.figure.aggregator.api.route.toOffsetDateTime
import io.ktor.http.HttpStatusCode
import tech.figure.aggregator.api.cache.CacheService.Companion.DEFAULT_LIMIT
import tech.figure.aggregator.api.cache.CacheService.Companion.DEFAULT_OFFSET
import tech.figure.aggregator.api.cache.json

fun NormalOpenAPIRoute.txRoute(cacheService: CacheService){

    tag(Tag.Transaction) {
        route("transaction/out") {
            get<TxRequest, TxResponse>(
                info(
                    summary = "Get a list of transaction out data for a given address within a set date range."
                ),
                example = TxResponse.sampleTxResponse
            ) { param ->

                val result: List<TxCoinTransferData> = cacheService.getTxOut(
                        param.address,
                        param.startDate.toOffsetDateTime(),
                        param.endDate.toOffsetDateTime(),
                        param.limit?.toInt() ?: DEFAULT_LIMIT,
                        param.offset?.toInt() ?: DEFAULT_OFFSET
                    )

                if(result.isEmpty()) {
                    respond(TxResponse("No records found", HttpStatusCode.NotFound))
                } else {
                    respond(TxResponse(result.json(), HttpStatusCode.OK))
                }
            }
        }

        route("transaction/in") {
            get<TxRequest, TxResponse>(
                info(
                    summary = "Get a list of transaction in data for a given address within a set date range."
                ),
                example = TxResponse.sampleTxResponse
            ) { param ->

                val result: List<TxCoinTransferData> = cacheService.getTxIn(
                    param.address,
                    param.startDate.toOffsetDateTime(),
                    param.endDate.toOffsetDateTime(),
                    param.limit?.toInt() ?: DEFAULT_LIMIT,
                    param.offset?.toInt() ?: DEFAULT_OFFSET
                )
                if(result.isEmpty()) {
                    respond(TxResponse("No records found", HttpStatusCode.NotFound))
                } else {
                    respond(TxResponse(result.toString(), HttpStatusCode.OK))
                }
            }
        }

        route("transaction/net") {
            get<TxRequest, TxResponse>(
                info(
                    summary = "Get the net denom transaction for a given address within a set date range"
                ),
                example = TxResponse.sampleNetTxResponse
            ) { param ->
                if (param.address == "" || param.startDate == "" || param.endDate == "" || param.denom == "") {
                    throws(
                        HttpStatusCode.BadRequest.description("Invalid parameters"),
                        example = OptionalResult.FAIL,
                        exClass = JsonProcessingException::class
                    )
                }
                val response = cacheService.getNetDateRangeTx(
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
