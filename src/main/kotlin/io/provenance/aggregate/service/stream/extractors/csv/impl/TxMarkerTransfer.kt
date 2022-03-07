package io.provenance.aggregate.service.stream.extractors.csv.impl

import io.provenance.aggregate.common.extensions.toISOString
import io.provenance.aggregate.service.stream.extractors.csv.CSVFileExtractor
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.models.provenance.marker.EventMarker
import io.provenance.aggregate.service.stream.repository.db.DBInterface

data class MarkerTransferDB(
    val event_type: String?,
    val block_height: Long?,
    val block_timestamp: String?,
    val amount: String?,
    val denom: String?,
    val administrator: String?,
    val to_address: String?,
    val from_address: String?
)

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
    override suspend fun extract(block: StreamBlock, dbRepository: DBInterface<Any>) {
        for (event in block.txEvents) {
            EventMarker.mapper.fromEvent(event)
                ?.let { record: EventMarker ->
                    when (record) {
                        is EventMarker.Transfer -> {
                            val markerTransferData = MarkerTransferDB(
                                event.eventType,
                                event.blockHeight,
                                event.blockDateTime?.toISOString(),
                                record.amount,
                                record.denom,
                                record.administrator,
                                record.toAddress,
                                record.fromAddress
                            )
                            syncWriteRecord(
                                markerTransferData,
                                includeHash = true
                            ).also { hash ->
                                dbRepository.save(hash = hash, markerTransferData)
                            }
                        }
                        else -> {
                            // noop
                        }
                    }
                }
        }
    }
}
