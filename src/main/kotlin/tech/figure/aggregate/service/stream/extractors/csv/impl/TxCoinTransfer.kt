package tech.figure.aggregate.service.stream.extractors.csv.impl

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.domain.CoinTransferTable
import tech.figure.aggregate.common.toISOString
import tech.figure.aggregate.common.models.AmountDenom
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.service.stream.extractors.OutputType
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.extractors.csv.DBExtractor
import java.time.OffsetDateTime
import java.util.UUID
import tech.figure.aggregate.service.stream.models.cosmos.Tx as CosmosTx

data class CoinTransferData(
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

/**
 * Extract data related to the movement of coins between accounts
 */
class TxCoinTransfer : CSVFileExtractor(
    name = "tx_coin_transfer",
    headers = listOf(
        "hash",
        "event_type",
        "block_height",
        "block_timestamp",
        "tx_hash",
        "recipient",
        "sender",
        "amount",
        "denom"
    )
) {

    override suspend fun extract(block: StreamBlock) {
        for(txData in block.blockTxData) {
            for(event in txData.events) {
                tech.figure.aggregate.service.stream.models.cosmos.Tx.mapper.fromEvent(event)
                    ?.let { record: CosmosTx ->
                        when(record) {
                            is CosmosTx.Transfer -> {
                                val amountAndDenom: List<AmountDenom>? = record.amountAndDenom?.let { record.splitAmountAndDenom(it)}
                                amountAndDenom?.map { amountDenom ->
                                    transaction {
                                        CoinTransferTable.insert {
                                            it[id] = UUID.randomUUID()
                                            it[eventType] = event.eventType.toString()
                                            it[blockHeight] = event.blockHeight.toDouble()
                                            it[blockTimestamp] = event.blockDateTime!!
                                            it[txhash] = event.txHash
                                            it[recipient] = record.recipient.toString()
                                            it[sender] = record.sender.toString()
                                            it[amount] = amountDenom.amount
                                            it[denom] = amountDenom.denom
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}
