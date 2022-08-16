package com.provenance.aggregator.api.model.response

import com.papsign.ktor.openapigen.annotations.Response
import io.ktor.http.HttpStatusCode

@Response("Queried data response as per address and date range.")
data class TxResponse (
    val message: String,
    val statusCode: HttpStatusCode
) {
    companion object{
        val sampleTxResponse = TxResponse(
            message = "{\"addr\":\"tp1jxwmsatj22055002938ydhehwvkepq7d6dassx\",\"startDate\":\"2021-04-05T00:00Z\",\"endDate\":\"2021-04-06T00:00Z\",\"total\":-20925001,\"denom\":\"nhash\"}",
            statusCode = HttpStatusCode.OK
        )

        val sampleFeeResponse = TxResponse(
            message = "{\"addr\":\"tp1jxwmsatj22055002938ydhehwvkepq7d6dassx\",\"startDate\":\"2021-04-05T00:00Z\",\"endDate\":\"2021-04-06T00:00Z\",\"total\":21015000,\"denom\":\"nhash\"}",
            statusCode = HttpStatusCode.OK
        )
    }
}
