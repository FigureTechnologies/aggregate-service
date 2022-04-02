package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.eventstream.stream.models.StreamBlock

/**
 * Extract data related to errored transfers that collected a fee.
 */
class TxError: CSVFileExtractor(
    name = "tx_errors",
    headers = listOf(
        "hash",
        "block_height",
        "block_timestamp",
        "error_code",
        "info"
    )
) {
    override suspend fun extract(block: StreamBlock) {
        for(error in block.txErrors) {
            syncWriteRecord(
                error.blockHeight,
                error.blockDateTime?.toISOString(),
                error.code,
                error.info,
                includeHash = true
            )
        }
    }
}
