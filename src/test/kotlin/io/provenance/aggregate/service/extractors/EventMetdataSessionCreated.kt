package io.provenance.aggregate.service.test.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.common.models.StreamBlock

/**
 * Extract transaction attributes (add, update, delete, delete distinct) to CSV.
 */
class EventMetdataSessionCreated() : CSVFileExtractor(
    "tx_event_metadata_session",
    listOf("event_type", "block_height", "block_timestamp", "session_addr", "scope_addr")
) {
    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            if (event.eventType == "provenance.metadata.v1.EventSessionCreated") {
                val eventMap = event.toDecodedMap()
                syncWriteRecord(
                    event.eventType,
                    event.blockHeight,
                    event.blockDateTime?.toISOString(),
                    eventMap["session_addr"],
                    eventMap["scope_addr"]
                )
            }
        }
    }
}
