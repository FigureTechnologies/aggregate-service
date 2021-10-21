package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.service.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.StreamBlock
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
        "recipient",
        "sender",
        "amount",
        "denom",
    )
) {
    /**
     * Given a string like `12275197065nhash`, where the amount and denomination are concatenated, split the string
     * into separate amount and denomination strings.
     *
     * To determine amount, consume as many numeric values from the string until a non-numeric value is encountered.
     */
    private fun splitAmountAndDenom(str: String): Pair<String, String> {
        val amount = StringBuilder(str)
        val denom = StringBuilder()
        for (i in str.length - 1 downTo 0) {
            val ch = str[i]
            if (!ch.isDigit()) {
                amount.deleteCharAt(i)
                denom.insert(0, ch)
            } else {
                break
            }
        }
        return Pair(amount.toString(), denom.toString())
    }

    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            CosmosTx.mapper.fromEvent(event)
                ?.let { record: CosmosTx ->
                    when (record) {
                        is CosmosTx.Transfer -> {
                            val (amount, denom) = splitAmountAndDenom(record.amountAndDenom)
                            syncWriteRecord(
                                event.eventType,
                                event.blockHeight,
                                event.blockDateTime?.toISOString(),
                                record.recipient,
                                record.sender,
                                amount,
                                denom,
                                includeHash = true
                            )
                        }
                    }
                }
        }
    }
}
