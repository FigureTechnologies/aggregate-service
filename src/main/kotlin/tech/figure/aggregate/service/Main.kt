package tech.figure.aggregate.service

import tech.figure.aggregator.api.route.configureRouting
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import com.timgroup.statsd.NoOpStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import io.grpc.LoadBalancerRegistry
import io.ktor.application.install
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import tech.figure.aggregate.common.Config
import tech.figure.aggregate.common.recordMaxBlockHeight
import tech.figure.aggregate.common.unwrapEnvOrError
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.common.models.UploadResult
import tech.figure.aggregate.service.stream.consumers.EventStreamUploader
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.slf4j.Logger
import tech.figure.aggregate.common.Environment
import tech.figure.aggregate.repository.database.RavenDB
import tech.figure.aggregate.service.flow.extensions.cancelOnSignal
import tech.figure.block.api.client.BlockAPIClient
import tech.figure.block.api.proto.BlockServiceOuterClass
import tech.figure.block.api.proto.BlockServiceOuterClass.PREFER
import java.util.Properties
import kotlin.time.Duration
import io.grpc.internal.PickFirstLoadBalancerProvider
import kotlinx.coroutines.flow.catch
import tech.figure.aggregate.common.snowflake.SnowflakeClient
import tech.figure.block.api.client.GRPCConfigOpt
import tech.figure.block.api.client.Protocol.TLS
import tech.figure.block.api.client.withApiKey
import tech.figure.block.api.client.withProtocol
import kotlin.system.exitProcess

/**
 * Installs a shutdown a handler to clean up resources when the returned Channel receives its one (and only) element.
 * This is primary intended to be used to clean up resources allocated by Flows.
 */
private fun installShutdownHook(log: Logger): Channel<Unit> {
    val signal = Channel<Unit>(1)
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() = runBlocking {
            log.warn("Sending cancel signal")
            signal.send(Unit)
            delay(500)
            log.warn("Shutting down")
        }
    })
    return signal
}

private fun withManagedChannelConfig(maxBlockSize: Int): GRPCConfigOpt = {
    channel.maxInboundMessageSize(maxBlockSize)
}

@OptIn(FlowPreview::class, kotlin.time.ExperimentalTime::class)
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {

    LoadBalancerRegistry.getDefaultRegistry().register(PickFirstLoadBalancerProvider())

    /**
     * All configuration options can be overridden via environment variables:
     *
     * - To override nested configuration options separated with a dot ("."), use double underscores ("__")
     *   in the environment variable:
     *     event.stream.rpc_uri=http://localhost:26657 is overridden by "event__stream_rpc_uri=foo"
     *
     * @see https://github.com/sksamuel/hoplite#environmentvariablespropertysource
     */
    val parser = ArgParser("aggregate-service")
    val envFlag by parser.option(
        ArgType.Choice<Environment>(),
        shortName = "e",
        fullName = "env",
        description = "Specify the application environment. If not present, fall back to the `\$ENVIRONMENT` envvar",
    )
    val fromHeight by parser.option(
        ArgType.Int,
        fullName = "from",
        description = "Fetch blocks starting from height, inclusive."
    )
    val toHeight by parser.option(
        ArgType.Int, fullName = "to", description = "Fetch blocks up to height, inclusive"
    )
    val restart by parser.option(
        ArgType.Boolean,
        fullName = "restart",
        description = "Restart processing blocks from the last maximum historical block height recorded"
    ).default(false)
    val skipIfEmpty by parser.option(
        ArgType.Choice(listOf(false, true), { it.toBooleanStrict() }),
        fullName = "skip-if-empty",
        description = "Skip blocks that have no transactions"
    ).default(false)
    val skipIfSeen by parser.option(
        ArgType.Choice(listOf(false, true), { it.toBooleanStrict() }),
        fullName = "skip-if-seen",
        description = "Skip blocks that have already been seen (stored in DynamoDB)"
    ).default(true)
    val ddHostFlag by parser.option(
        ArgType.String, fullName = "dd-host", description = "Datadog agent metrics will be sent to"
    )
    val ddTagsFlag by parser.option(
        ArgType.String, fullName = "dd-tags", description = "Datadog tags that will be sent with every metric"
    )

    parser.parse(args)

    val ddEnabled = runCatching { System.getenv("DD_ENABLED") }.getOrNull() == "true"
    val ddHost = ddHostFlag ?: runCatching { System.getenv("DD_AGENT_HOST") }.getOrElse { "localhost" }
    val ddTags = ddTagsFlag ?: runCatching { System.getenv("DD_TAGS") }.getOrElse { "" }

    val properties = Properties().apply {
        put("user", unwrapEnvOrError("DW_USER"))
        put("password", unwrapEnvOrError("DW_PASSWORD"))
        put("warehouse", unwrapEnvOrError("DW_WAREHOUSE"))
        put("db", unwrapEnvOrError("DW_DATABASE"))
        put("schema", unwrapEnvOrError("DW_SCHEMA"))
        put("networkTimeout", "30")
        put("queryTimeout", "30")
    }

    val dwUri = "jdbc:snowflake://${unwrapEnvOrError("DW_HOST")}.snowflakecomputing.com"

    val environment: Environment =
        envFlag ?: runCatching { Environment.valueOf(System.getenv("ENVIRONMENT")) }
            .getOrElse {
                error("Not a valid environment: ${System.getenv("ENVIRONMENT")}")
            }

    val config: Config = ConfigLoaderBuilder.default()
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .apply {
            // If in the local environment, override the ${...} envvar values in `application.properties` with
            // the values provided in the local-specific `local.env.properties` property file:
            if (environment.isLocal()) {
                addPreprocessor(PropsPreprocessor("/local.env.properties"))
            }
        }
        .addSource(PropertySource.resource("/application.yml"))
        .build()
        .loadConfigOrThrow()

    val log = "main".logger()

    val blockApiClient = BlockAPIClient(
        config.blockApi.host,
        config.blockApi.port,
        withProtocol(TLS),
        withApiKey(config.blockApi.apiKey),
        withManagedChannelConfig(config.blockApi.maxBlockSize)
    )

    val ravenClient = RavenDB(config.dbConfig)
    val dogStatsClient = if (ddEnabled) {
        log.info("Initializing Datadog client...")
        NonBlockingStatsDClientBuilder()
            .prefix("aggregate-service")
            .hostname(ddHost)
            .timeout(5_000)
            .enableTelemetry(false)
            .constantTags(*ddTags.split(" ").toTypedArray())
            .build()
    } else {
        log.info("Datadog client disabled.")
        NoOpStatsDClient()
    }

    val shutDownSignal: Channel<Unit> = installShutdownHook(log)

    runBlocking(Dispatchers.IO) {
        log.info(
            """
            |run options => {
            |    restart = $restart
            |    from-height = $fromHeight 
            |    to-height = $toHeight
            |    skip-if-empty = $skipIfEmpty
            |    skip-if-seen = $skipIfSeen
            |    dd-enabled = $ddEnabled
            |    dd-host = $ddHost
            |    dd-tags = $ddTags
            |}
            """.trimMargin("|")
        )

        // Update DataDog with the latest historical block height every minute:
        launch {
            while (true) {
                ravenClient.getBlockCheckpoint()
                    .also { log.info("Maximum block height: ${it ?: "--"}") }
                    ?.let(dogStatsClient::recordMaxBlockHeight)
                    ?.getOrElse { log.error("DD metric failure", it) }
                delay(Duration.minutes(1))
            }
        }

        val fromHeightGetter: suspend () -> Long? = {
            var maxHistoricalHeight: Long? =  ravenClient.getBlockCheckpoint()
            log.info("Start :: historical max block height = $maxHistoricalHeight")
            if (restart) {
                if (maxHistoricalHeight == null) {
                    log.warn("No historical max block height found; defaulting to 1")
                } else {
                    log.info("Restarting from historical max block height: ${maxHistoricalHeight + 1}")
                    // maxHistoricalHeight is last successful processed, to prevent processing this block height again
                    // we need to increment.
                    maxHistoricalHeight += 1
                }
                log.info(
                    "--restart: true, starting block height at: ${
                        maxOf(maxHistoricalHeight ?: 1, fromHeight?.toLong() ?: 1)
                    } }"
                )
                maxOf(maxHistoricalHeight ?: 1, fromHeight?.toLong() ?: 1)
            } else {
                log.info("--restart: false, starting from block height ${fromHeight?.toLong()}")
                fromHeight?.toLong()
            }
        }

        val snowflakeClient = SnowflakeClient(properties, dwUri)

        // start api
        async {
            embeddedServer(Netty, port=8081) {
                install(Routing) {
                    configureRouting(snowflakeClient, config.dbConfig, config.apiHost)
                }
            }.start(wait = true)
        }

        val blockFlow: Flow<BlockServiceOuterClass.BlockStreamResult> = blockApiClient.streamBlocks(147724 , PREFER.TX_EVENTS)
            EventStreamUploader(
                blockFlow,
                snowflakeClient,
                ravenClient,
                config.hrp,
                Pair(config.badBlockRange[0], config.badBlockRange[1]),
                config.msgFeeHeight
            )
                .addExtractor(config.upload.extractors)
                .upload()
                .cancelOnSignal(shutDownSignal)
                .catch {
                    // Reset on exception so that we can pick up
                    // the last successful checked block height
                    exitProcess(1)
                }
                .collect { result: UploadResult ->
                    log.info(
                        "uploaded #${result.batchId} => \n" +
                                "Key: ${result.s3Key} => \n" +
                                "Historical Block Height Range: ${result.blockHeightRange.first} - ${result.blockHeightRange.second}"
                    )
                }
        }
}

