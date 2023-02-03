package tech.figure.aggregator.api.route.v1

import com.fasterxml.jackson.core.JsonProcessingException
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import com.papsign.ktor.openapigen.route.throws
import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregator.api.cache.CacheService
import tech.figure.aggregator.api.model.request.TxRequest
import tech.figure.aggregator.api.route.Tag
import tech.figure.aggregator.api.route.exception.OptionalResult
import tech.figure.aggregator.api.route.toOffsetDateTime
import io.ktor.http.HttpStatusCode
import tech.figure.aggregator.api.cache.CacheService.Companion.DEFAULT_LIMIT
import tech.figure.aggregator.api.cache.CacheService.Companion.DEFAULT_OFFSET
import tech.figure.aggregator.api.model.TxDailyTotal
import tech.figure.aggregator.api.model.TxTotalAmtResponse

fun NormalOpenAPIRoute.txRoute(cacheService: CacheService){

    tag(Tag.Transaction) {
        route("transaction/out") {
            get<TxRequest, List<TxDailyTotal>>(
                info(
                    summary = "Get the list of daily out total transaction as per date range."
                ),
                example = TxDailyTotal.sampleResponse
            ) { param ->

                val result: List<TxDailyTotal> = cacheService.getTxOut(
                        param.address,
                        param.startDate.toOffsetDateTime(),
                        param.endDate.toOffsetDateTime(),
                        param.limit?.toInt() ?: DEFAULT_LIMIT,
                        param.offset?.toInt() ?: DEFAULT_OFFSET
                    )

                respond(result)
            }
        }

        route("transaction/raw/out") {
            get<TxRequest, List<TxCoinTransferData>>(
                info(
                    summary = "Get a list of all transaction out data for a given address within a set date range."
                ),
                example = TxCoinTransferData.sampleTxResponse
            ) { param ->

                val result: List<TxCoinTransferData> = cacheService.getTxOutRaw(
                    param.address,
                    param.startDate.toOffsetDateTime(),
                    param.endDate.toOffsetDateTime(),
                    param.limit?.toInt() ?: DEFAULT_LIMIT,
                    param.offset?.toInt() ?: DEFAULT_OFFSET
                )

                respond(result)
            }
        }

        route("transaction/raw/in/") {
            get<TxRequest, List<TxCoinTransferData>>(
                info(
                    summary = "Get a list of all transaction in data for a given address within a set date range."
                ),
                example = TxCoinTransferData.sampleTxResponse
            ) { param ->

                val result: List<TxCoinTransferData> = cacheService.getTxInRaw(
                    param.address,
                    param.startDate.toOffsetDateTime(),
                    param.endDate.toOffsetDateTime(),
                    param.limit?.toInt() ?: DEFAULT_LIMIT,
                    param.offset?.toInt() ?: DEFAULT_OFFSET
                )

                respond(result)
            }
        }


        route("transaction/in") {
            get<TxRequest, List<TxDailyTotal>>(
                info(
                    summary = "Get the list of daily in total transaction as per date range."
                ),
                example = TxDailyTotal.sampleResponse
            ) { param ->

                val result: List<TxDailyTotal> = cacheService.getTxIn(
                    param.address,
                    param.startDate.toOffsetDateTime(),
                    param.endDate.toOffsetDateTime(),
                    param.limit?.toInt() ?: DEFAULT_LIMIT,
                    param.offset?.toInt() ?: DEFAULT_OFFSET
                )

                respond(result)
            }
        }

        route("transaction/net") {
            get<TxRequest, TxTotalAmtResponse>(
                info(
                    summary = "Get the net denom transaction for a given address within a set date range"
                ),
                example = TxTotalAmtResponse.sampleResponse
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
