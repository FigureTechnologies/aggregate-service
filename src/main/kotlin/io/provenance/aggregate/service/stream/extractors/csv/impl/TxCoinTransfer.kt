package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.AmountDenom
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.provenance.cosmos.Tx as CosmosTx

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
            CosmosTx.mapper.fromEvent(event)
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
