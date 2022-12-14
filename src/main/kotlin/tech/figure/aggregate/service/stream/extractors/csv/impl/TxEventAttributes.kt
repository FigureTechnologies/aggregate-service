package tech.figure.aggregate.service.stream.extractors.csv.impl

import tech.figure.aggregate.common.toISOString
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.models.attribute.EventAttribute

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
        "owner"
    )
) {
    override suspend fun extract(block: StreamBlock) {
        for (blockData in block.blockTxData) {
            for(event in blockData.events) {
                EventAttribute.mapper.fromEvent(event)?.toEventRecord()
                    ?.let { record ->
                        // Output transformations that make the output data easier to work with:
                        // If `updatedValue` is non-null, write that, otherwise fallback to `value`
                        // If `updatedType` is non-null, write that, otherwise fallback to `type`
                        syncWriteRecord(
                            event.eventType,
                            event.blockHeight,
                            event.blockDateTime?.toISOString(),
                            record.name,
                            record.updatedValue ?: record.value,
                            record.updatedType ?: record.type,
                            record.account,
                            record.owner,
                            includeHash = true
                        )
                    }
            }
        }
    }
}
