package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.repository.db.DBInterface


data class ErrorsDB(
    val block_height: Long?,
    val block_timestamp: String?,
    val error_code: Long?,
    val info: String?
)

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
    override suspend fun extract(block: StreamBlock, dbRepository: DBInterface<Any>) {
        for(error in block.txErrors) {
            val errorData = ErrorsDB(
                error.blockHeight,
                error.blockDateTime?.toISOString(),
                error.code,
                error.info
            )

            syncWriteRecord(
                errorData,
                includeHash = true
            ).also { hash ->
                dbRepository.save(hash = hash, errorData)
            }
        }
    }
}
