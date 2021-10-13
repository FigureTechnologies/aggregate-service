package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.provenance.attribute.EventAttribute

/**
 * Extract transaction attributes (add, update, delete, delete distinct) to CSV.
 */
class TxEventAttributes(val s3: AwsS3Interface) : CSVFileExtractor(
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
    ),
    generateHash = true
) {
    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            EventAttribute.mapper.fromEvent(event)
                ?.toEventRecord()
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
                        record.owner
                    )
                }
        }
    }
}