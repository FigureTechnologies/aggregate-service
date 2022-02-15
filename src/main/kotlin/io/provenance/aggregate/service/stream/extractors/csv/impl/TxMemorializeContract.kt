package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.provenance.memorialization.MemorializeContract

/**
 * Extract data related to contract memorialization
 */
class TxMemorializeContract: CSVFileExtractor(
    name = "tx_memorialize_contract",
    headers = listOf(
        "hash",
        "event_action_type",
        "block_height",
        "block_timestamp"
    )
) {
    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            MemorializeContract.mapper.fromEvent(event)
                ?.let { record ->
                    when(record) {
                        is MemorializeContract.Message ->
                            if(record.isMemorializeRequest()) {
                                syncWriteRecord(
                                    record.action,
                                    event.blockHeight,
                                    event.blockDateTime,
                                    includeHash = true
                                )
                            }
                    }
                }
        }
    }
}
