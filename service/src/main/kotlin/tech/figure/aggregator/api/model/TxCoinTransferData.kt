package tech.figure.aggregator.api.model

data class TxCoinTransferData(
    val hash: String,
    val eventType: String,
    val blockHeight: Double,
    val blockTimestamp: String,
    val txHash: String,
    val recipient: String,
    val sender: String,
    val amount: String,
    val denom: String
) {
    companion object{
        val sampleTxResponse = listOf(
            TxCoinTransferData(
                hash = "123ABC",
                eventType = "transfer",
                blockHeight = 123.0,
                blockTimestamp = "2022-08-24 00:48:42.245254649",
                txHash = "ABC123",
                recipient = "tx123",
                sender = "tx456",
                amount = "100",
                denom = "nhash"
            )
        )
    }
}
