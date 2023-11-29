package tech.figure.aggregate.common.db

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.exposed.sql.transactions.transaction
import tech.figure.aggregate.common.channel.ChannelImpl
import tech.figure.aggregate.common.domain.AttributesRecord
import tech.figure.aggregate.common.domain.CheckpointRecord
import tech.figure.aggregate.common.domain.CoinTransferRecord
import tech.figure.aggregate.common.domain.FeeRecords
import tech.figure.aggregate.common.domain.MarkerSupplyRecord
import tech.figure.aggregate.common.domain.MarkerTransferRecord
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.common.models.stream.CoinTransfer
import tech.figure.aggregate.common.models.stream.MarkerSupply
import tech.figure.aggregate.common.models.stream.MarkerTransfer
import tech.figure.aggregate.common.models.stream.impl.StreamTypeImpl
import tech.figure.aggregate.common.toOffsetDateTime
import java.io.File
import tech.figure.aggregate.common.domain.CheckpointTable

class DBClient: DBJdbc() {

    private val log = logger()

    fun handleInsert(
        name: String,
        csvFile: File,
        channel: ChannelImpl<StreamTypeImpl>
    ) {

        val csvFile = CSVParser(csvFile.bufferedReader(), CSVFormat.DEFAULT.withFirstRecordAsHeader())
        when (name) {
            "tx_coin_transfer" -> {
                csvFile.records.onEach { csvRecord ->
                    val record = CoinTransfer(
                        csvRecord["event_type"],
                        csvRecord["block_height"].toLong(),
                        csvRecord["block_timestamp"],
                        csvRecord["tx_hash"],
                        csvRecord["recipient"],
                        csvRecord["sender"],
                        csvRecord["amount"],
                        csvRecord["denom"]
                    )

                    CoinTransferRecord.insert(
                        csvRecord["hash"],
                        record.eventType.toString(),
                        record.blockHeight.toDouble(),
                        record.blockTimestamp!!.toOffsetDateTime(),
                        record.txHash,
                        record.recipient ?: "",
                        record.sender ?: "",
                        record.amount,
                        record.denom
                    )

                    channel.send(record)
                }
            }

            "tx_fees" -> {
                csvFile.records.onEach { csvRecord ->
                    FeeRecords.insert(
                        csvRecord["hash"],
                        csvRecord["tx_hash"],
                        csvRecord["block_height"].toDouble(),
                        csvRecord["block_timestamp"].toOffsetDateTime(),
                        csvRecord["fee"],
                        csvRecord["fee_denom"],
                        csvRecord["sender"]
                    )
                }
            }

            "tx_event_attributes" -> {
                csvFile.records.onEach { csvRecord ->
                    AttributesRecord.insert(
                        csvRecord["hash"],
                        csvRecord["event_type"],
                        csvRecord["block_height"].toDouble(),
                        csvRecord["block_timestamp"].toOffsetDateTime(),
                        csvRecord["name"],
                        csvRecord["value"],
                        csvRecord["type"],
                        csvRecord["account"],
                        csvRecord["owner"]
                    )
                }
            }

            "tx_marker_transfer" -> {
                csvFile.records.onEach { csvRecord ->
                    val record = MarkerTransfer(
                        csvRecord["event_type"],
                        csvRecord["block_height"].toLong(),
                        csvRecord["block_timestamp"],
                        csvRecord["tx_hash"],
                        csvRecord["amount"],
                        csvRecord["denom"],
                        csvRecord["administrator"],
                        csvRecord["to_address"],
                        csvRecord["from_address"]
                    )

                    MarkerTransferRecord.insert(
                        csvRecord["hash"],
                        record.eventType,
                        record.blockHeight.toDouble(),
                        record.blockTimestamp.toOffsetDateTime(),
                        record.txHash,
                        record.amount,
                        record.denom,
                        record.administrator,
                        record.toAddress,
                        record.fromAddress
                    )

                    channel.send(record)
                }
            }

            "tx_marker_supply" -> {
                csvFile.records.onEach { csvRecord ->

                    val record = MarkerSupply(
                        csvRecord["event_type"],
                        csvRecord["block_height"].toLong(),
                        csvRecord["block_timestamp"],
                        csvRecord["tx_hash"],
                        csvRecord["coins"],
                        csvRecord["denom"],
                        csvRecord["amount"],
                        csvRecord["administrator"],
                        csvRecord["to_address"],
                        csvRecord["from_address"],
                        csvRecord["metadata_base"],
                        csvRecord["metadata_description"],
                        csvRecord["metadata_display"],
                        csvRecord["metadata_denom_units"],
                        csvRecord["metadata_name"],
                        csvRecord["metadata_symbol"]
                    )

                    MarkerSupplyRecord.insert(
                        csvRecord["hash"],
                        record.eventType ?: "",
                        record.blockHeight.toDouble(),
                        record.blockTimestamp!!.toOffsetDateTime(),
                        record.txHash ?: "",
                        record.coins ?: "",
                        record.denom ?: "",
                        record.amount ?: "",
                        record.administrator ?: "",
                        record.toAddress ?: "",
                        record.fromAddress ?: "",
                        record.metadataBase ?: "",
                        record.metadataDescription ?: "",
                        record.metadataDisplay ?: "",
                        record.metadataDenomUnits ?: "",
                        record.metadataName ?: "",
                        record.metadataSymbol ?: ""
                    )

                    channel.send(record)
                }
            }
        }
    }

    fun writeCheckpoint(height: Long) {
        log.info("writeCheckpoint($height)")
        transaction { CheckpointRecord.upsert(height) }
    }

    fun getLastKnownCheckpoint(): Long? {
        return transaction { CheckpointRecord.findLastKnownBlockHeight()?.let { it[CheckpointTable.blockHeight] } }
    }
}
