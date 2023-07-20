package tech.figure.aggregate.service.stream.extractors.csv.impl

import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.common.toISOString
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.models.marker.EventMarker

/**
 * Extract data related to the overall supply of a marker.
 */

class TxMarkerSupply : CSVFileExtractor(
    name = "tx_marker_supply",
    headers = listOf(
        "hash",
        "event_type",
        "block_height",
        "block_timestamp",
        "tx_hash",
        "coins",
        "denom",
        "amount",
        "administrator",
        "to_address",
        "from_address",
        "metadata_base",
        "metadata_description",
        "metadata_display",
        "metadata_denom_units",
        "metadata_name",
        "metadata_symbol"
    )
) {
    override suspend fun extract(block: StreamBlock) {
        for (txData in block.blockTxData) {
            for(event in txData.events) {
                EventMarker.mapper.fromEvent(event)
                    ?.toEventRecord()
                    ?.let { record ->
                        // All transfers are processed by `TxMarkerTransfer`
                        if (!record.isTransfer()) {
                            syncWriteRecord(
                                event.eventType,
                                event.blockHeight,
                                event.blockDateTime?.toISOString(),
                                event.txHash,
                                record.coins,
                                record.denom,
                                record.amount,
                                record.administrator,
                                record.toAddress,
                                record.fromAddress,
                                record.metadataBase,
                                record.metadataDescription,
                                record.metadataDisplay,
                                record.metadataDenomUnits,
                                record.metadataName,
                                record.metadataSymbol
                            )
                        }
                    }
            }
        }
    }
}
