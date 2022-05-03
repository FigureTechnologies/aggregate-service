package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor

/**
 * Extract data related to errored transfers that collected a fee.
 */
class TxError: CSVFileExtractor(
    name = "tx_errors",
    headers = listOf(
        "hash",
        "tx_hash",
        "block_height",
        "block_timestamp",
        "error_code",
        "info",
        "signer_addr",
        "fee",
        "fee_denom"
    ),
) {
    override suspend fun extract(block: StreamBlock) {
        for(error in block.txErrors) {
            syncWriteRecord(
                error.txHash,
                error.blockHeight,
                error.blockDateTime?.toISOString(),
                error.code,
                error.info,
                error.fee.signerAddr,
                error.fee.fee,
                error.fee.denom,
                includeHash = true
            )
        }
    }
}
