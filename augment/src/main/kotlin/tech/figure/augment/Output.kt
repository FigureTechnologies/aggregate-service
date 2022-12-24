/**
 * COMMENT OUT FOR NOW, WE MAY NEED THIS FOR THE SECONDARY, RIGHT NOW NOT BEING USED.
 */
//package tech.figure.augment
//
//import tech.figure.aggregate.common.AwsConfig
//import tech.figure.aggregate.common.S3Config
//import tech.figure.aggregate.common.aws.AwsClient
//import tech.figure.aggregate.common.aws.s3.S3Bucket
//import tech.figure.aggregate.common.snowflake.Key
//import tech.figure.aggregate.common.aws.s3.StreamableObject
//import tech.figure.aggregate.common.writer.csv.ApacheCommonsCSVRecordWriter
//import io.provenance.eventstream.config.Environment
//import org.apache.commons.csv.CSVFormat
//import org.slf4j.Logger
//import software.amazon.awssdk.core.async.AsyncRequestBody
//import tech.figure.augment.dsl.Data
//import tech.figure.augment.dsl.LoggingOutput
//import tech.figure.augment.dsl.Output
//import tech.figure.augment.dsl.S3Output
//import java.io.BufferedOutputStream
//import java.io.OutputStreamWriter
//import java.nio.file.Files
//import java.nio.file.StandardOpenOption
//import java.time.OffsetDateTime
//import java.util.*
//
//fun Data.filterColumns(output: Output): Data {
//    val columns = when (output) {
//        is LoggingOutput -> output.columns
//        is S3Output -> output.columns
//    }.toSortedSet()
//
//    return this.map { row ->
//        row.filterKeys(columns::contains)
//    }
//}
//
//suspend fun Data.output(environment: Environment, jobName: String, output: Output, log: Logger): Unit = when (output) {
//    is LoggingOutput -> log.info("LoggingOutput result = $this")
//    is S3Output -> {
//        val config = AwsConfig(
//            region = System.getenv("AWS_REGION"),
//            S3Config(S3Bucket(output.bucket))
//        )
//        val client = AwsClient.create(config.s3)
//        val outputFile = Files.createTempFile("", "staging_file.csv")
//        val outputStream = BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE))
//        val writer = ApacheCommonsCSVRecordWriter.Builder()
//            .format(
//                CSVFormat.Builder.create()
//                    .apply { setHeader(*output.columns.toTypedArray()) }
//                    .build()
//            )
//            .output(OutputStreamWriter(outputStream))
//            .build()
//
//        writer.use {
//            this.forEach { row ->
//                it.writeRecord(*output.columns.map(row::getValue).toTypedArray())
//            }
//        }
//
//        val key = Key.create(OffsetDateTime.now(), "cron", UUID.randomUUID().toString(), "${output.tableName}.csv")
//        client.s3().streamObject(object : StreamableObject {
//            override val key: Key get() = key
//            override val body: AsyncRequestBody get() = AsyncRequestBody.fromFile(outputFile)
//            override val metadata: Map<String, String> get() = mapOf(
//                "JobName" to jobName,
//                "TableName" to output.tableName,
//            )
//        })
//
//        log.info("$jobName output file written to ${key.value}")
//    }
//}
