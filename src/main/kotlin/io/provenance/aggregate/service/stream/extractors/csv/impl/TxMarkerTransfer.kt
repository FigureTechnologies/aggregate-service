package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.provenance.marker.EventMarker

/**
 * Extract data related to the transfer of a marker between parties.
 */
class TxMarkerTransfer(val s3: AwsS3Interface) : CSVFileExtractor(
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
    ),
    generateHash = true
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
                                record.fromAddress
                            )
                        else -> {
                            // noop
                        }
                    }
                }
        }
    }
}
