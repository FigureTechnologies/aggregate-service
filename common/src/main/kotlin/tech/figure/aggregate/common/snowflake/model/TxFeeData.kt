package tech.figure.aggregate.common.snowflake.model

data class TxFeeData(
    val hash: String,
    val txHash: String,
    val blockHeight: Double,
    val blockTimestamp: String,
    val fee: String,
    val feeDenom: String,
    val sender: String
) {
    companion object {
        val sampleResponse = listOf(
            TxFeeData(
                hash = "ABC123",
                txHash = "123ABC",
                blockHeight = 1000.0,
                blockTimestamp = "2021-04-06T00:00Z",
                fee = "123",
                feeDenom = "nhash",
                sender = "tx123"
            )
        )
    }
}
