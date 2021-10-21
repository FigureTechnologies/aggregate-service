package tech.figure.augment

import io.grpc.ManagedChannelBuilder
import io.provenance.aggregate.common.Environment
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import org.apache.commons.dbutils.ResultSetIterator
import org.slf4j.LoggerFactory
import tech.figure.augment.db.sql
import tech.figure.augment.dsl.DbSource
import tech.figure.augment.dsl.Job
import tech.figure.augment.dsl.RpcSource
import tech.figure.augment.dsl.emptyData
import tech.figure.augment.provenance.ProvenanceClient
import tech.figure.augment.provenance.query
import java.net.URI
import java.sql.DriverManager
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("main")
    val job = Json.decodeFromString(Job.serializer(), System.getenv("JOB_JSON"))
    val environment: Environment = runCatching { Environment.valueOf(System.getenv("ENVIRONMENT")) }
        .getOrElse {
            error("Not a valid environment: ${System.getenv("ENVIRONMENT")}")
        }

    log.info("Running job - ${job.name}")
    log.info("Job config - ${Json.encodeToString(Job.serializer(), job)}")

    val properties = Properties().apply {
        put("user", System.getenv("DB_USER"))
        put("password", System.getenv("DB_PASSWORD"))
        put("warehouse", System.getenv("DB_WAREHOUSE"))
        put("db", System.getenv("DB_DATABASE"))
        put("schema", System.getenv("DB_SCHEMA"))
        put("networkTimeout", "30")
        put("queryTimeout", "30")
    }
    val dbConnection = DriverManager.getConnection("jdbc:snowflake://${System.getenv("DB_HOST")}.snowflakecomputing.com", properties)

    val provenanceUri = URI(System.getenv("PROVENANCE_GRPC_URL"))
    val channel = ManagedChannelBuilder
        .forAddress(provenanceUri.host, provenanceUri.port)
        .also {
            if (provenanceUri.scheme.endsWith("s")) {
                it.useTransportSecurity()
            } else {
                it.usePlaintext()
            }
        }
        .build()
    val semaphore = Semaphore(System.getenv("GRPC_CONCURRENCY")?.toInt() ?: 10)
    val provenanceClient = ProvenanceClient(channel, semaphore)

    runBlocking {
        val latestBlock = provenanceClient.getLatestBlock()
        val latestBlockTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(latestBlock.block.header.time.seconds), ZoneOffset.UTC)

        // Set default fields present on every row. These may not be used in all queries.
        val defaultData = mapOf(
            "timestamp" to latestBlockTime.format(DateTimeFormatter.ISO_DATE_TIME).toString(),
            "date" to latestBlockTime.toLocalDate().format(DateTimeFormatter.ISO_DATE).toString(),
            "height" to latestBlock.block.header.height.toString(),
        )

        val sourceStepResult = job.query.sources.fold(emptyData()) { acc, source ->
            when (source) {
                // TODO DbSource does not currently account for a populated accumulator
                // this means it only supports queries where there's one DbSource and it's the first source
                is DbSource -> {
                    val (sql, params) = sql(source)
                    val result = runBlocking {
                        val statement = dbConnection.prepareStatement(sql.value).apply {
                            params.value.forEachIndexed { index, param -> setString(index + 1, param) }
                        }
                        ResultSetIterator(statement.executeQuery())
                    }.asFlow().toList(mutableListOf())

                    // TODO acc is useless here based on the TODO above
                    acc + result.map { row ->
                        source.columns.mapIndexed { index, column ->
                            column to row[index].toString()
                        }.toMap() + defaultData
                    }
                }
                // TODO RpcSource does not currently account for a blank accumulator
                // this means it only supports queries where there's one DbSource before the RpcSource
                is RpcSource -> {
                    query(provenanceClient, source, acc)
                }
            }
        }

        // TODO implement transformations when needed

        sourceStepResult
            .filterColumns(job.query.output)
            .output(environment, job.name, job.query.output, log)
    }
}
