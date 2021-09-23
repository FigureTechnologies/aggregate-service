package io.provenance.aggregate.service.stream.extractors.csv

import io.provenance.aggregate.service.aws.s3.AwsS3Interface
import io.provenance.aggregate.service.stream.extractors.Extractor
import io.provenance.aggregate.service.stream.extractors.OutputType
import io.provenance.aggregate.service.stream.models.ProvenanceEventAttribute
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.writer.csv.ApacheCommonsCSVRecordWriter
import org.apache.commons.csv.CSVFormat
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Extract transaction attributes (add, update, delete, delete distinct) to CSV.
 */
class TxEventAttributes(val s3: AwsS3Interface) : Extractor {

    override val name: String = "tx-event-attributes"

    private val outputFile: Path = Files.createTempFile("${name}-", ".csv")

    private val outputStream: OutputStream =
        //BufferedOutputStream(TeeOutputStream(FileOutputStream(outputFile), System.out))
        Files.newOutputStream(outputFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE)

    val headers: Array<String> =
        arrayOf("height", "name", "value", "updatedValue", "type", "updatedType", "account", "owner")

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

    override fun output(): OutputType = OutputType.FilePath(outputFile)

    override suspend fun extract(block: StreamBlock) {
        for (e in block.txEvents) {
            ProvenanceEventAttribute.createFromEventType(e.eventType, e.height, e.toDecodedMap())
                ?.toEventRecord()
                ?.let { record ->
                    synchronized(this) {
                        writer.writeRecord(
                            record.height,
                            record.name,
                            record.value,
                            record.updatedValue,
                            record.type,
                            record.updatedType,
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