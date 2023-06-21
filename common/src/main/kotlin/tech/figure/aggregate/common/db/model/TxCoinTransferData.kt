package tech.figure.aggregate.common.db.model


import tech.figure.aggregate.common.db.model.impl.TxResponseData
import java.sql.Timestamp

data class TxCoinTransferData(
    val hash: String,
    val eventType: String,
    val blockHeight: Long,
    val blockTimestamp: Timestamp,
    val txHash: String,
    val recipient: String,
    val sender: String,
    val amount: String,
    val denom: String
) : TxResponseData {
    companion object{
        val sampleTxResponse = listOf(
            TxCoinTransferData(
                hash = "123ABC",
                eventType = "transfer",
                blockHeight = 123,
                blockTimestamp = Timestamp.valueOf("2021-08-24 06:01:52"),
                txHash = "ABC123",
                recipient = "tx123",
                sender = "tx456",
                amount = "100",
                denom = "nhash"
            )
        )
    }
}
