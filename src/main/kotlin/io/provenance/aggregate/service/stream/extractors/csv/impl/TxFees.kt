package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.provenance.cosmos.Tx

class TxFees: CSVFileExtractor(
    name = "tx_fees",
    headers = listOf(
        "hash",
        "tx_hash",
        "block_height",
        "block_timestamp",
        "fee",
        "fee_denom"
    )
) {

    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            Tx.mapper.fromEvent(event)
                ?.let { record: Tx ->
                    when (record) {
                        is Tx.Transfer -> {
                            /*
                            *   Write the transaction fees to another table so that we can
                            *   more accurately track fees per Tx versus per transfer record.
                            */
                            syncWriteRecord(
                                event.txHash,
                                event.blockHeight,
                                event.blockDateTime,
                                event.fee,
                                event.feeDenom,
                                includeHash = true
                            )
                        }
                    }
                }
        }
    }
}
