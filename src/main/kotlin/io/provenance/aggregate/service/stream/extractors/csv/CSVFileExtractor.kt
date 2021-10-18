package io.provenance.aggregate.service.stream.extractors.csv

import io.provenance.aggregate.service.extensions.*
import io.provenance.aggregate.service.stream.extractors.FileExtractor
import io.provenance.aggregate.service.stream.extractors.OutputType
import io.provenance.aggregate.service.utils.sha256
import io.provenance.aggregate.service.writer.csv.ApacheCommonsCSVRecordWriter
import org.apache.commons.csv.CSVFormat
import java.io.OutputStreamWriter

/**
 * An extractor that generates a CSV file.
 *
 * @property name The base name of the output file, sans extension.
 * @property headers An optional list of headers. If provided, the first output record will consist of the headers
 * provided.
 */
abstract class CSVFileExtractor(
    /**
     * The filename base to use for the output file.
     */
    name: String,

    /**
     * If provided, these headers will be used when generating the output file.
     */
    val headers: Iterable<String>? = null,
) : FileExtractor(name, suffix = ".csv") {
    /**
     * The underlying CSV writer implementation used to produce output.
     */
    protected val writer: ApacheCommonsCSVRecordWriter = ApacheCommonsCSVRecordWriter
        .Builder()
        .format(
            CSVFormat.Builder
                .create()
                .apply {
                    if (headers != null) {
                        val headers: Array<String> = headers.toList().toTypedArray()
                        setHeader(*headers)
                    }
                }
                .build()
        )
        .output(OutputStreamWriter(outputStream))
        .build()

    @Volatile
    private var flagWriteOutput: Boolean = false


    /**
     * Synchronously write a record to the CSV output writer using a synchronized(<lock>) { ... }
     *
     * If `includeHash` is true, the computed hash value will be the first entry of the written record.
     */
    fun syncWriteRecord(vararg values: Any?, includeHash: Boolean = true) {
        synchronized(this) {
            flagWriteOutput = true
            if (includeHash) {
                val hash = computeRowHash(*values)
                writer.writeRecord(hash, *values)
            } else {
                writer.writeRecord(*values)
            }
        }
    }

    /**
     * Compute the hash of a given row, returning a hex-encoded string.
     */
    fun computeRowHash(vararg values: Any?): String = sha256(values.mapNotNull { it?.toString() }).toHexString()

    /**
     * Only files with more than 1 now, excluding header, should actually be written.
     *
     * This will be set upon the first call to `syncWriteRecords.
     *
     * @see flagWriteOutput
     * @see syncWriteRecord
     */
    override fun shouldOutput(): Boolean = flagWriteOutput

    /**
     * Tests if the output will include a header row.
     */
    fun hasHeaders(): Boolean = headers != null

    /**
     * This extractor always produces file output, along with some metadata about the file itself:
     *
     * - The suggested name of the table the data should be stored in.
     */
    override fun output(): OutputType {
        return OutputType.FilePath(
            outputFile,
            metadata = mapOf(
                "TableName" to name
            )
        )
    }

    // https://stackoverflow.com/questions/67476133/upload-a-inputstream-to-aws-s3-asynchronously-non-blocking-using-aws-sdk-for-j
    override suspend fun beforeComplete() {
        writer.close()
    }
}