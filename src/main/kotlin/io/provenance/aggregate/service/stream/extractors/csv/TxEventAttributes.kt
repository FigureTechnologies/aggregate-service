package io.provenance.aggregate.service.stream.extractors.csv

import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.stream.extractors.Extractor
import io.provenance.aggregate.service.stream.extractors.OutputType
import io.provenance.aggregate.service.stream.models.ProvenanceEventAttribute
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.writer.csv.ApacheCommonsCSVRecordWriter
import org.apache.commons.csv.CSVFormat
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Extract transaction attributes (add, update, delete, delete distinct) to CSV.
 */
class TxEventAttributes(val s3: AwsS3Interface) : Extractor {

    override val name: String = "TX_EVENT_ATTRIBUTES"

    private val outputFile: Path = Files.createTempFile("${name}-", ".csv")

    private val outputStream: OutputStream =
        BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE))

    val headers: Array<String> =
        arrayOf("eventType", "height", "name", "value", "type", "account", "owner")

    private val writer: ApacheCommonsCSVRecordWriter = ApacheCommonsCSVRecordWriter
        .Builder()
        .format(
            CSVFormat.Builder
                .create()
                .setHeader(*headers)
                .build()
        )
        .output(OutputStreamWriter(outputStream))
        .build()

    override fun output(): OutputType {
        return OutputType.FilePath(
            outputFile,
            metadata = mapOf(
                "TableName" to name
            )
        )
    }

    override suspend fun extract(block: StreamBlock) {
        for (e in block.txEvents) {
            ProvenanceEventAttribute.createFromEventType(e.eventType, e.height, e.toDecodedMap())
                ?.toEventRecord()
                ?.let { record ->
                    synchronized(this) {
                        // Output transformations that make the output data easier to work with:
                        // If `updatedValue` is non-null, write that, otherwise fallback to `value`
                        // If `updatedType` is non-null, write that, otherwise fallback to `type`
                        writer.writeRecord(
                            e.eventType,
                            record.height,
                            record.name,
                            record.updatedValue ?: record.value,
                            record.updatedType ?: record.type,
                            record.account,
                            record.owner
                        )
                    }
                }
        }
    }

    // https://stackoverflow.com/questions/67476133/upload-a-inputstream-to-aws-s3-asynchronously-non-blocking-using-aws-sdk-for-j
    override suspend fun beforeComplete() {
        writer.close()
    }

    override fun close() {
        Files.deleteIfExists(outputFile)
    }
}