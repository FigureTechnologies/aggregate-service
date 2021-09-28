package tech.figure.augment

import com.github.jasync.sql.db.ConnectionPoolConfigurationBuilder
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tech.figure.augment.db.sql
import tech.figure.augment.dsl.DbSource
import tech.figure.augment.dsl.Job
import tech.figure.augment.dsl.RpcSource
import tech.figure.augment.dsl.emptyData
import tech.figure.augment.dsl.job
import tech.figure.augment.provenance.ProvenanceClient
import tech.figure.augment.provenance.query
import java.net.URI
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(object {}::class.java)
    // val job = Json.decodeFromString(Job.serializer(), System.getenv("JOB_JSON"))
    val job = job {
        name = "test-1"
        cron = "cron text"
        query {
            dbSource {
                table = "attributes"
                column { "address" }
                filter {
                    left = "key"
                    right = "nycb.kyc.pb"
                    operator = "="
                }
            }
            rpcSource {
                module = "bank"
                filter {
                    setter = "denom"
                    value = "nhash"
                }
            }
            loggingOutput {
                column { "address" }
                column { "balance" }
                column { "timestamp" }
                column { "block_height" }
            }
        }
    }

    log.info("Running job - ${job.name}")
    log.info("Job config - ${Json.encodeToString(Job.serializer(), job)}")

    val dbConfig = ConnectionPoolConfigurationBuilder().apply {
        host = "localhost"
        database = "object-store"
        maxActiveConnections = 2
        maxIdleTime = TimeUnit.MINUTES.toMillis(5)
        maxPendingQueries = 5
        queryTimeout = TimeUnit.SECONDS.toMillis(10)
        username = "postgres"
        password = "password1"
    }
    val dbConnection = PostgreSQLConnectionBuilder.createConnectionPool(dbConfig)

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
    val semaphore = Semaphore(System.getenv("GRPC_CONCURRENCY")?.toInt() ?: 20)
    val provenanceClient = ProvenanceClient(channel, semaphore)

    runBlocking {
        val latestBlock = provenanceClient.getLatestBlock()

        // Set default fields present on every row. These may not be used in all queries.
        val defaultData = mapOf(
            "timestamp" to latestBlock.block.header.time.seconds.toString(),
            "block_height" to latestBlock.block.header.height.toString(),
        )

        val sourceStepResult = job.query.sources.fold(emptyData()) { acc, source ->
            when (source) {
                // TODO DbSource does not currently account for a populated accumulator
                // this means it only supports queries where there's one DbSource and it's the first source
                is DbSource -> {
                    val (sql, params) = sql(source)
                    val result = dbConnection.sendPreparedStatement(sql.value, params.value, true).await()

                    // TODO acc is useless here based on the TODO above
                    acc + result.rows.map { row ->
                        source.columns.associateWith { column ->
                            row[column] as String
                        } + defaultData
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
            .output(job.query.output, log)
    }
}
