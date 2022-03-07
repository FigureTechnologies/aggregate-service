package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.common.models.Constants
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.models.provenance.attribute.EventAttribute
import io.provenance.aggregate.service.stream.repository.db.DBInterface

data class AttributesDB(
    val event_type: String?,
    val block_height: Long?,
    val block_timestamp: String?,
    val name: String?,
    val value: String?,
    val type: String?,
    val account: String?,
    val owner: String?,
    val fee: Long?,
    val fee_denom: String? = Constants.FEE_DENOMINATION
)

/**
 * Extract transaction attributes (add, update, delete, delete distinct) to CSV.
 */
class TxEventAttributes : CSVFileExtractor(
    name = "tx_event_attributes",
    headers = listOf(
        "hash",
        "event_type",
        "block_height",
        "block_timestamp",
        "name",
        "value",
        "type",
        "account",
        "owner",
        "fee",
        "fee_denom"
    )
) {
    override suspend fun extract(block: StreamBlock, dbRepository: DBInterface<Any>) {
        for (event in block.txEvents) {
            EventAttribute.mapper.fromEvent(event)?.toEventRecord()
                ?.let { record ->
                    val attributeData = AttributesDB(
                        event.eventType,
                        event.blockHeight,
                        event.blockDateTime?.toISOString(),
                        record.name,
                        record.updatedValue ?: record.value,
                        record.updatedType ?: record.type,
                        record.account,
                        record.owner,
                        event.fee,
                        event.feeDenom
                    )

                    // Output transformations that make the output data easier to work with:
                    // If `updatedValue` is non-null, write that, otherwise fallback to `value`
                    // If `updatedType` is non-null, write that, otherwise fallback to `type`
                    syncWriteRecord(
                        attributeData,
                        includeHash = true
                    ).also { hash ->
                        dbRepository.save(hash = hash, attributeData)
                    }
                }
        }
    }
}
