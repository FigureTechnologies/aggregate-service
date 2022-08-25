package tech.figure.aggregator.api.model

import com.papsign.ktor.openapigen.annotations.Response

@Response("Address data found", statusCode = 200)
data class TxTotalAmtResponse(
    val addr: String,
    val startDate: String,
    val endDate: String,
    val total: Long,
    val denom: String
) {
    companion object {
        val sampleResponse = TxTotalAmtResponse(
            addr = "tx123",
            startDate = "2022-08-24",
            endDate = "2022-08-25",
            total = 123455,
            denom = "nhash"
        )
    }
}
