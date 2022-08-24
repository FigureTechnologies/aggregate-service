package tech.figure.aggregate.service.stream.extractors.csv.impl

import tech.figure.aggregate.common.toISOString
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.common.models.AmountDenom
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.models.cosmos.Tx as CosmosTx

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
        for (event in block.txEvents) {
            tech.figure.aggregate.service.stream.models.cosmos.Tx.mapper.fromEvent(event)
                ?.let { record: CosmosTx ->
                    when (record) {
                        is CosmosTx.Transfer -> {
                            val amountAndDenom: List<AmountDenom>? = record.amountAndDenom?.let { record.splitAmountAndDenom(it) }
                            amountAndDenom?.map { amountDenom ->
                                syncWriteRecord(
                                    event.eventType,
                                    event.blockHeight,
                                    event.blockDateTime?.toISOString(),
                                    event.txHash,
                                    record.recipient,
                                    record.sender,
                                    amountDenom.amount,  // amount
                                    amountDenom.denom,  // denom
                                    includeHash = true
                                )
                            }
                        }
                    }
                }
        }
    }
}