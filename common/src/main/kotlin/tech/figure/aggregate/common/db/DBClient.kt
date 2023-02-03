package tech.figure.aggregate.common.db

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import tech.figure.aggregate.common.domain.AttributesRecord
import tech.figure.aggregate.common.domain.CoinTransferRecord
import tech.figure.aggregate.common.domain.FeeRecords
import tech.figure.aggregate.common.domain.MarkerSupplyRecord
import tech.figure.aggregate.common.domain.MarkerTransferRecord
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.common.toOffsetDateTime
import java.io.File

class DBClient: DBJdbc() {

    private val log = logger()

    fun handleInsert(name: String, csvFile: File) {
        val csvFile = CSVParser(csvFile.bufferedReader(), CSVFormat.DEFAULT.withFirstRecordAsHeader())
        when (name) {
            "tx_coin_transfer" -> {
                csvFile.records.onEach { csvRecord ->
                     CoinTransferRecord.insert(
                        csvRecord["hash"],
                        csvRecord["event_type"],
                        csvRecord["block_height"].toDouble(),
                        csvRecord["block_timestamp"].toOffsetDateTime(),
                        csvRecord["tx_hash"],
                        csvRecord["recipient"],
                        csvRecord["sender"],
                        csvRecord["amount"],
                        csvRecord["denom"]
                    )
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
                    MarkerTransferRecord.insert(
                        csvRecord["hash"],
                        csvRecord["event_type"],
                        csvRecord["block_height"].toDouble(),
                        csvRecord["block_timestamp"].toOffsetDateTime(),
                        csvRecord["amount"],
                        csvRecord["denom"],
                        csvRecord["administrator"],
                        csvRecord["to_address"],
                        csvRecord["from_address"]
                    )
                }
            }

            "tx_marker_supply" -> {
                csvFile.records.onEach { csvRecord ->
                    MarkerSupplyRecord.insert(
                        csvRecord["hash"],
                        csvRecord["event_type"],
                        csvRecord["block_height"].toDouble(),
                        csvRecord["block_timestamp"].toOffsetDateTime(),
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
                }
            }
        }
    }
}
