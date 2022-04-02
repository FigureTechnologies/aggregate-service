package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.models.AmountDenom
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.provenance.cosmos.Tx
import io.provenance.eventstream.stream.models.StreamBlock

class TxFees: CSVFileExtractor(
    name = "tx_fees",
    headers = listOf(
        "hash",
        "tx_hash",
        "block_height",
        "block_timestamp",
        "fee",
        "fee_denom",
        "sender"
    )
) {

    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            Tx.mapper.fromEvent(event)
                ?.let { record: Tx ->
                    when (record) {
                        is Tx.Transfer -> {
                            /*
                            *   If the recipient is the fee collector then
                            *   write fees to this table.
                            */
                            val amountAndDenom: List<AmountDenom>? = record.amountAndDenom?.let { record.splitAmountAndDenom(it) }
                            amountAndDenom?.map { amountDenom ->
                                syncWriteRecord(
                                    event.txHash,
                                    event.blockHeight,
                                    event.blockDateTime,
                                    event.fee,
                                    event.denom,
                                    record.sender, // wallet addr that is paying the fee collector
                                    includeHash = true
                                )
                            }
                        }
                    }
                }
        }
    }
}
