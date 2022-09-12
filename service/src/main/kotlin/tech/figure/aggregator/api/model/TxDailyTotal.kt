package tech.figure.aggregator.api.model

data class TxDailyTotal(
    val address: String,
    val date: String,
    val total: Long,
    val denom: String
) {
    companion object {
        val sampleResponse = listOf(
            TxDailyTotal(
                address = "123abc",
                date = "2022-01-01",
                total = 100,
                denom = "nhash"
            )
        )
    }
}
