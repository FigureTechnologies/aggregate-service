package tech.figure.aggregate.service.stream.extractors.csv.impl

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.domain.FeesTable
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import java.util.UUID

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
        for (blockTxData in block.blockTxData) {
            for(event in blockTxData.events) {
                if(event.eventType == "transfer" || event.eventType == "ERROR") {
                    syncWriteRecord(
                        event.txHash,
                        event.blockHeight,
                        event.blockDateTime,
                        blockTxData.fee.fee,
                        blockTxData.fee.denom,
                        blockTxData.fee.signerInfo?.incurrAddr
                    )
                }
            }
        }
    }
}
