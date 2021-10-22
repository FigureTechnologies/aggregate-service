package io.provenance.aggregate.common.writer.csv

import io.provenance.aggregate.common.writer.RecordWriter
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.Closeable
import java.lang.Appendable

data class ApacheCommonsCSVRecordWriter private constructor(private val writer: CSVPrinter) : RecordWriter, Closeable {

    class Builder {
        var output: Appendable = System.out
        var format: CSVFormat = CSVFormat.DEFAULT

        fun output(value: Appendable) = apply { output = value }
        fun format(value: CSVFormat) = apply { format = value }
        fun build(): ApacheCommonsCSVRecordWriter = ApacheCommonsCSVRecordWriter(CSVPrinter(output, format))
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    override fun writeRecord(vararg values: Any?): ApacheCommonsCSVRecordWriter {
        writer.printRecord(*values)
        return this
    }

    override fun close() {
        writer.close(true)
    }
}
