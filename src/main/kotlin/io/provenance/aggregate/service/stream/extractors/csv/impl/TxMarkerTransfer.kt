package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.provenance.marker.EventMarker
import io.provenance.eventstream.stream.models.StreamBlock

/**
 * Extract data related to the transfer of a marker between parties.
 */
class TxMarkerTransfer : CSVFileExtractor(
    name = "tx_marker_transfer",
    headers = listOf(
        "hash",
        "event_type",
        "block_height",
        "block_timestamp",
        "amount",
        "denom",
        "administrator",
        "to_address",
        "from_address"
    )
) {
    override suspend fun extract(block: StreamBlock) {
        for (event in block.txEvents) {
            EventMarker.mapper.fromEvent(event)
                ?.let { record: EventMarker ->
                    when (record) {
                        is EventMarker.Transfer ->
                            syncWriteRecord(
                                event.eventType,
                                event.blockHeight,
                                event.blockDateTime?.toISOString(),
                                record.amount,
                                record.denom,
                                record.administrator,
                                record.toAddress,
                                record.fromAddress,
                                includeHash = true
                            )
                        else -> {
                            // noop
                        }
                    }
                }
        }
    }
}
