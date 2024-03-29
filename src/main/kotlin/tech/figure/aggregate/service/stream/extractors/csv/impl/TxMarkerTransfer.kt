package tech.figure.aggregate.service.stream.extractors.csv.impl

import tech.figure.aggregate.common.toISOString
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.models.marker.EventMarker

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
        "tx_hash",
        "amount",
        "denom",
        "administrator",
        "to_address",
        "from_address"
    )
) {
    override suspend fun extract(block: StreamBlock) {
        for (blockTxData in block.blockTxData) {
            for(event in blockTxData.events) {
                EventMarker.mapper.fromEvent(event)
                    ?.let { record: EventMarker ->
                        when (record) {
                            is EventMarker.Transfer ->
                                syncWriteRecord(
                                    event.eventType,
                                    event.blockHeight,
                                    event.blockDateTime?.toISOString(),
                                    event.txHash,
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
}
