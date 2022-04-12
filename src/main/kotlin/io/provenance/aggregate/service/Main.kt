package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.EnvironmentVariablesPropertySource
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
import com.timgroup.statsd.NoOpStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import io.provenance.aggregate.common.Config
import io.provenance.aggregate.common.aws.AwsClient
import io.provenance.aggregate.common.extensions.recordMaxBlockHeight
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.UploadResult
import io.provenance.aggregate.common.models.extensions.toStreamBlock
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.aggregate.repository.factory.RepositoryFactory
import io.provenance.aggregate.service.stream.consumers.EventStreamUploader
import io.provenance.eventstream.config.Environment
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.extensions.repeatDecodeBase64
import io.provenance.eventstream.flow.extensions.cancelOnSignal
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.flows.blockFlow
import io.provenance.eventstream.utils.colors.green
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import org.slf4j.Logger
import kotlin.time.Duration

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

@OptIn(FlowPreview::class, kotlin.time.ExperimentalTime::class)
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {
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
    val observe by parser.option(
        ArgType.Boolean, fullName = "observe", description = "Observe blocks instead of upload"
    ).default(false)
    val restart by parser.option(
        ArgType.Boolean,
        fullName = "restart",
        description = "Restart processing blocks from the last maximum historical block height recorded"
    ).default(false)
    val verbose by parser.option(
        ArgType.Boolean, shortName = "v", fullName = "verbose", description = "Enables verbose output"
    ).default(false)
    val ordered by parser.option(
        ArgType.Boolean,
        fullName = "ordered",
        description = "Stream from the event stream in order"
    ).default(true)
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

    val environment: Environment =
        envFlag ?: runCatching { Environment.valueOf(System.getenv("ENVIRONMENT")) }
            .getOrElse {
                error("Not a valid environment: ${System.getenv("ENVIRONMENT")}")
            }

    val config: Config = ConfigLoader.Builder()
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

    val netAdapter = okHttpNetAdapter(config.wsNode)
    val aws: AwsClient = AwsClient.create(environment, config.aws.s3, config.aws.dynamodb)
    val repository = RepositoryFactory(config.dbConfig).dbInstance()
    val dynamo = aws.dynamo()
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
                dynamo.getMaxHistoricalBlockHeight()
                    .also { log.info("Maximum block height: ${it ?: "--"}") }
                    ?.let(dogStatsClient::recordMaxBlockHeight)
                    ?.getOrElse { log.error("DD metric failure", it) }
                delay(Duration.minutes(1))
            }
        }

        // This function will be used by the event stream to determine the current maximum historical block height.
        // It will be called at the start of the event stream, as well as any time the event stream has to be restarted.
        // In the event of restart, if progress was made on the maximum block height since the stream was initialized,
        // we need to compute the updated max starting height on demand to know where to start again, rather than in the
        // past.
        //
        // Regarding how this is computed:
        //   Figure out if there's a maximum historical block height that's been seen already.
        //   - If `--restart` is provided and a max height was found, try to start from there.
        //   - If no height exists, `--restart` implies that the caller is interested in historical blocks,
        //     so issue a warning and start at 1.
        //
        // Note: `--restart` can be combined with `--from=HEIGHT`. If both are given, the maximum value
        // will be chosen as the starting height

        val fromHeightGetter: suspend () -> Long? = {
            var maxHistoricalHeight: Long? =  dynamo.getMaxHistoricalBlockHeight()
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

        val options = BlockStreamOptions.create(
            withFromHeight(fromHeightGetter()),
            withToHeight(toHeight?.toLong()),
            withSkipEmptyBlocks(skipIfEmpty),
            withOrdered(ordered)
        )

        val blockFlow: Flow<BlockData> = blockFlow(netAdapter, moshiDecoderAdapter(), from = options.fromHeight, to = options.toHeight)
        if (observe) {
            blockFlow
                .transform { emit(it.toStreamBlock()) }
                .onEach {
                    val text = "Block: ${it.block.header?.height ?: "--"}:${it.block.header?.dateTime()?.toLocalDate()}"
                    println(green(text))
                    if (verbose) {
                        for (event in it.blockEvents) {
                            println("  Block-Event: ${event.eventType}")
                            for (attr in event.attributes) {
                                println("    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}")
                            }
                        }
                        for (event in it.txEvents) {
                            println(" Tx-Event: ${event.eventType}")
                            for (attr in event.attributes) {
                                println("    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}")
                            }
                        }
                    }
            }
        } else {
            if (config.upload.extractors.isNotEmpty()) {
                log.info("upload: adding extractors")
                for (event in config.upload.extractors) {
                    log.info(" - $event")
                }
            }

            EventStreamUploader(
                blockFlow,
                aws,
                repository,
                options
            )
                .addExtractor(config.upload.extractors)
                .upload()
                .cancelOnSignal(shutDownSignal)
                .collect { result: UploadResult ->
                    log.info(
                        "uploaded #${result.batchId} => \n" +
                                "S3 ETag: ${result.eTag} => \n" +
                                "S3Key: ${result.s3Key} => \n" +
                                "Historical Block Height Range: ${result.blockHeightRange.first} - ${result.blockHeightRange.second}"
                    )
                }
        }
    }
}

