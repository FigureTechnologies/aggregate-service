package tech.figure.aggregate.service.stream.extractors.csv.impl

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.domain.MarkerSupplyTable
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.service.stream.extractors.csv.CSVFileExtractor
import tech.figure.aggregate.service.stream.models.marker.EventMarker
import java.util.UUID

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
                             transaction {
                                 MarkerSupplyTable.insert {
                                     it[id] = UUID.randomUUID()
                                     it[eventType] = event.eventType ?: ""
                                     it[blockHeight] = event.blockHeight.toDouble()
                                     it[blockTimestamp] = event.blockDateTime!!
                                     it[coins] = record.coins ?: ""
                                     it[denom] = record.denom ?: ""
                                     it[amount] = record.amount ?: ""
                                     it[administrator] = record.administrator ?: ""
                                     it[toAddress] = record.toAddress ?: ""
                                     it[fromAddress] = record.fromAddress ?: ""
                                     it[metadataBase] = record.metadataBase ?: ""
                                     it[metadataDescription] = record.metadataDescription ?: ""
                                     it[metadataDisplay] = record.metadataDisplay ?: ""
                                     it[metadataDenomUnits] = record.metadataDenomUnits ?: ""
                                     it[metadataName] = record.metadataName ?: ""
                                     it[metadataSymbol] = record.metadataSymbol ?: ""
                                 }
                             }
                        }
                    }
            }
        }
    }
}
