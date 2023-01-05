package tech.figure.aggregate.common.snowflake

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import tech.figure.aggregate.common.logger
import java.io.File
import java.util.Properties

class SnowflakeClient(
    properties: Properties,
    dwUri: String
): SnowflakeJDBC(properties, dwUri) {

    private val log = logger()

    fun handleInsert(name: String, csvFile: File) {
        val csvFile = CSVParser(csvFile.bufferedReader(), CSVFormat.DEFAULT.withFirstRecordAsHeader())
        val csvRows = mutableListOf<String>()

        when(name) {
            "tx_coin_transfer" -> {
                csvFile.records.onEach {
                    val dataValue = """
                        '${it["hash"]}',
                        '${it["event_type"]}',
                        '${it["block_height"]}',
                        '${it["block_timestamp"]}',
                        '${it["tx_hash"]}',
                        '${it["recipient"]}',
                        '${it["sender"]}',
                        '${it["amount"]}',
                        '${it["denom"]}'
                        """.trimIndent()
                    csvRows.add("($dataValue)")
                }
                upsert(conn, csvRows, "COIN_TRANSFER", log)
            }

            "tx_fees" -> {
                csvFile.records.onEach {

                    val dataValue = """
                        '${it["hash"]}',
                        '${it["tx_hash"]}',
                        '${it["block_height"]}',
                        '${it["block_timestamp"]}',
                        '${it["fee"]}',
                        '${it["fee_denom"]}',
                        '${it["sender"]}'
                        """.trimIndent()
                    if(!csvRows.contains("($dataValue)")) {
                        csvRows.add("($dataValue)")
                    }
                }
                upsert(conn, csvRows, "FEES", log)
            }

            "tx_event_attributes" -> {
                csvFile.records.onEach {

                    val dataValue = """
                        '${it["hash"]}',
                        '${it["event_type"]}',
                        '${it["block_height"]}',
                        '${it["block_timestamp"]}',
                        '${it["name"]}',
                        '${it["value"]}',
                        '${it["type"]}',
                        '${it["account"]}',
                        '${it["owner"]}'
                        """.trimIndent()
                    if(!csvRows.contains("($dataValue)")) {
                        csvRows.add("($dataValue)")
                    }
                }
                upsert(conn, csvRows, "ATTRIBUTES", log)
            }

            "tx_marker_transfer" -> {
                csvFile.records.onEach {

                    val dataValue = """
                        '${it["hash"]}',
                        '${it["event_type"]}',
                        '${it["block_height"]}',
                        '${it["block_timestamp"]}',
                        '${it["amount"]}',
                        '${it["denom"]}',
                        '${it["administrator"]}',
                        '${it["to_address"]}',
                        '${it["from_address"]}'
                        """.trimIndent()
                    if(!csvRows.contains("($dataValue)")) {
                        csvRows.add("($dataValue)")
                    }
                }
                upsert(conn, csvRows, "MARKER_TRANSFER", log)
            }

            "tx_marker_supply" -> {
                csvFile.records.onEach {

                    val dataValue = """
                        '${it["hash"]}',
                        '${it["event_type"]}',
                        '${it["block_height"]}',
                        '${it["block_timestamp"]}',
                        '${it["coins"]}',
                        '${it["denom"]}',
                        '${it["amount"]}',
                        '${it["administrator"]}',
                        '${it["to_address"]}',
                        '${it["from_address"]}',
                        '${it["metadata_base"]}',
                        '${it["metadata_description"]}',
                        '${it["metadata_display"]}',
                        '${it["metadata_denom_units"]}',
                        '${it["metadata_name"]}',
                        '${it["metadata_symbol"]}'
                        """.trimIndent()
                    if(!csvRows.contains("($dataValue)")) {
                        csvRows.add("($dataValue)")
                    }
                }
                upsert(conn, csvRows, "MARKER_SUPPLY", log)
            }
        }
    }
}
