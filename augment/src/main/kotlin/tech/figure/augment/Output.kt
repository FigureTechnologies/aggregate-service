package tech.figure.augment

import tech.figure.aggregate.common.writer.csv.ApacheCommonsCSVRecordWriter
import io.provenance.eventstream.config.Environment
import org.apache.commons.csv.CSVFormat
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import tech.figure.aggregate.common.db.DBClient
import tech.figure.augment.dsl.Data
import tech.figure.augment.dsl.LoggingOutput
import tech.figure.augment.dsl.Output
import tech.figure.augment.dsl.ResultOutput
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption

fun Data.filterColumns(output: Output): Data {
    val columns = when (output) {
        is LoggingOutput -> output.columns
        is ResultOutput -> output.columns
    }.toSortedSet()

    return this.map { row ->
        row.filterKeys(columns::contains)
    }
}

suspend fun Data.output(environment: Environment, jobName: String, output: Output, log: Logger): Unit = when (output) {
    is LoggingOutput -> log.info("LoggingOutput result = $this")
    is ResultOutput -> {
        val dbClient = DBClient()
        val outputFile = Files.createTempFile("", "staging_file.csv")
        val outputStream = BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE))
        val writer = ApacheCommonsCSVRecordWriter.Builder()
            .format(
                CSVFormat.Builder.create()
                    .apply { setHeader(*output.columns.toTypedArray()) }
                    .build()
            )
            .output(OutputStreamWriter(outputStream))
            .build()

        writer.use {
            this.forEach { row ->
                it.writeRecord(*output.columns.map(row::getValue).toTypedArray())
            }
        }

        transaction {
            dbClient.handleInsert("nycb_usdf_balances", outputFile.toFile())
        }

        log.info("$jobName output file written")
    }
}
