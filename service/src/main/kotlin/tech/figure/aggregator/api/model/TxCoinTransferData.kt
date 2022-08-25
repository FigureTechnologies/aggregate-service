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
)
