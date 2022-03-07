package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.provenance.memorialization.MemorializeContract
import io.provenance.aggregate.service.stream.repository.db.DBInterface

data class MemorializeContractDB(
    val event_action_type: String?,
    val block_height: Long?,
    val block_timestamp: String?
)

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
    override suspend fun extract(block: StreamBlock, dbRepository: DBInterface<Any>) {
        for (event in block.txEvents) {
            MemorializeContract.mapper.fromEvent(event)
                ?.let { record ->
                    when(record) {
                        is MemorializeContract.Message ->
                            if(record.isMemorializeRequest()) {
                                val memorializeData = MemorializeContractDB(
                                    record.action,
                                    event.blockHeight,
                                    event.blockDateTime?.toISOString(),
                                )
                                syncWriteRecord(
                                    memorializeData,
                                    includeHash = true
                                ).also { hash ->
                                    dbRepository.save(hash = hash, memorializeData)
                                }
                            }
                    }
                }
        }
    }
}
