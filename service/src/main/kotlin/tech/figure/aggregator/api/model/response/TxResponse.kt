package tech.figure.aggregator.api.model.response

import com.papsign.ktor.openapigen.annotations.Response
import io.ktor.http.HttpStatusCode

@Response("Queried data response as per address and date range.")
data class TxResponse (
    val message: String,
    val statusCode: HttpStatusCode
) {
    companion object{
        val sampleNetTxResponse = TxResponse(
            message = "{\"addr\":\"tp1jxwmsatj22055002938ydhehwvkepq7d6dassx\",\"startDate\":\"2021-04-05T00:00Z\",\"endDate\":\"2021-04-06T00:00Z\",\"total\":-20925001,\"denom\":\"nhash\"}",
            statusCode = HttpStatusCode.OK
        )

        val sampleNetFeeResponse = TxResponse(
            message = "{\"addr\":\"tp1jxwmsatj22055002938ydhehwvkepq7d6dassx\",\"startDate\":\"2021-04-05T00:00Z\",\"endDate\":\"2021-04-06T00:00Z\",\"total\":21015000,\"denom\":\"nhash\"}",
            statusCode = HttpStatusCode.OK
        )

        val sampleTxResponse = TxResponse(
            message = "[{\"hash\":\"740447230B132211F104104108ED64EE6A5373764B01AB33F7C5F1A491592659\",\"eventType\":\"transfer\",\"blockHeight\":9914238.0,\"blockTimestamp\":\"2022-08-24 00:48:42.245254649\",\"txHash\":\"427CBAC7F343EF01492A77CCB13059D59A94A696AA3F7DF4571CDD766E1EF69C\",\"recipient\":\"tp17xpfvakm2amg962yls6f84z3kell8c5l2udfyt\",\"sender\":\"tp1t7j3638ke7hzgrjp9w4mvghpdudaqz5hdr6jn5\",\"amount\":\"158772225\",\"denom\":\"nhash\"}]",
            statusCode = HttpStatusCode.OK
        )

        val sampleFeeResponse = TxResponse(
            message = "[{\"hash\":\"0A3AC4A552D6157751741B7DA272438BEB8A865B2FA321E40A8DD76E92B39E17\",\"txHash\":\"null\",\"blockHeight\":9473127.0,\"blockTimestamp\":\"2022-08-01 00:50:04.802727312\",\"fee\":\"1587722250\",\"feeDenom\":\"nhash\",\"sender\":\"tp1t7j3638ke7hzgrjp9w4mvghpdudaqz5hdr6jn5\"}]",
            statusCode = HttpStatusCode.OK
        )
    }
}
