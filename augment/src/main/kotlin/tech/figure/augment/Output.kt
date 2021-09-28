package tech.figure.augment

import org.slf4j.Logger
import tech.figure.augment.dsl.Data
import tech.figure.augment.dsl.LoggingOutput
import tech.figure.augment.dsl.Output
import tech.figure.augment.dsl.S3Output

fun Data.filterColumns(output: Output): Data {
    val columns = when (output) {
        is LoggingOutput -> output.columns
        is S3Output -> output.columns
    }.toSortedSet()

    return this.map { row ->
        row.filterKeys(columns::contains)
    }
}

fun Data.output(output: Output, log: Logger): Unit = when (output) {
    is LoggingOutput -> log.info("LoggingOutput result = $this")
    is S3Output -> Unit
}
