package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.EnvironmentVariablesPropertySource
import com.sksamuel.hoplite.PropertySource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.aws.dynamodb.NoOpDynamo
import io.provenance.aggregate.service.extensions.decodeBase64
import io.provenance.aggregate.service.stream.*
import io.provenance.aggregate.service.adapter.json.JSONObjectAdapter
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.stream.models.UploadResult
import io.provenance.aggregate.service.stream.models.extensions.dateTime
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

fun configureEventStreamBuilder(websocketUri: String): Scarlet.Builder {
    val node = URI(websocketUri)
    return Scarlet.Builder()
        .webSocketFactory(
            OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
                .newWebSocketFactory("${node.scheme}://${node.host}:${node.port}/websocket")
        )
        .addMessageAdapterFactory(MoshiMessageAdapter.Factory())
        .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
}

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {

    // All configuration options can be overridden via environment variables:
    //
    // - ENVIRONMENT=development will override the application.properties value "environment=local".
    //
    // - To override nested configuration options separated with a dot ("."), use double underscores ("__")
    //  in the environment variable:
    //    event.stream.rpc_uri=http://localhost:26657 is overridden by "event__stream_rpc_uri=foo"
    //
    // See https://github.com/sksamuel/hoplite#environmentvariablespropertysource

    val parser = ArgParser("aggregate-service")
    val fromHeight by parser.option(
        ArgType.Int, fullName = "from", description = "Fetch blocks starting from height, inclusive"
    )
    val toHeight by parser.option(
        ArgType.Int, fullName = "to", description = "Fetch blocks up to height, inclusive"
    )
    val viewOnly by parser.option(
        ArgType.Boolean, fullName = "view", description = "View blocks instead of upload"
    ).default(false)
    val verbose by parser.option(
        ArgType.Boolean, shortName = "v", fullName = "verbose", description = "Enables verbose output"
    ).default(false)
    val skipIfEmpty by parser.option(
        ArgType.Boolean, fullName = "skip-if-empty", description = "Skip blocks that have no transactions"
    ).default(true)
    val skipIfSeen by parser.option(
        ArgType.Boolean,
        fullName = "skip-if-seen",
        description = "Skip blocks that have already been seen (stored in DynamoDB)"
    ).default(false)

    parser.parse(args)

    val config: Config = ConfigLoader.Builder()
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .addSource(PropertySource.resource("/application.properties"))
        .build()
        .loadConfig<Config>()
        .getUnsafe()

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()
    val wsStreamBuilder = configureEventStreamBuilder(config.event.stream.websocketUri)
    val tendermintService = TendermintServiceClient(config.event.stream.rpcUri)
    val aws: AwsInterface = AwsInterface.create(config.environment, config.s3, config.dynamodb)

    val log = object {}.logger()

    runBlocking(Dispatchers.IO) {

        // https://github.com/provenance-io/provenance/blob/v1.7.1/docs/proto-docs.md#provenance.attribute.v1.AttributeType
        val checkForEvents = setOf(
            "provenance.attribute.v1.EventAttributeAdd",
            "provenance.attribute.v1.EventAttributeUpdate",
            "provenance.attribute.v1.EventAttributeDelete",
            "provenance.attribute.v1.EventAttributeDistinctDelete"
        )

        val options = EventStream.Options
            .builder()
            .fromHeight(fromHeight?.let { it.toLong() })
            .toHeight(toHeight?.let { it.toLong() })
            .skipIfEmpty(skipIfEmpty)
            .skipIfSeen(skipIfSeen)
            .matchTxEvent { it in checkForEvents }
            .build()

        if (viewOnly) {
            log.info("*** VIEW MODE ***")
            val factory = EventStream.Factory(
                config,
                moshi,
                wsStreamBuilder,
                tendermintService,
                NoOpDynamo() // Don't record blocks that have been seen before in Dynamo
            )
            EventStreamViewer(factory, options)
                .consume { b: StreamBlock ->
                    val text =
                        "Block: ${b.block.header?.height ?: "--"}:${b.block.header?.dateTime()?.toLocalDate()}"
                    println(
                        if (b.historical) {
                            text
                        } else {
                            green(text)
                        }
                    )
                    if (verbose) {
                        for (event in b.blockEvents) {
                            println("  Block-Event: ${event.eventType}")
                            for (attr in event.attributes) {
                                println("    ${attr.key?.decodeBase64()}: ${attr.value?.decodeBase64()}")
                            }
                        }
                        for (event in b.txEvents.filter { it.eventType in checkForEvents }) {
                            println("  Tx-Event: ${event.eventType}")
                            for (attr in event.attributes) {
                                println("    ${attr.key?.decodeBase64()}: ${attr.value?.decodeBase64()}")
                            }
                        }
                    }
                    println()
                }
        } else {
            val factory = EventStream.Factory(
                config,
                moshi,
                wsStreamBuilder,
                tendermintService,
                aws.dynamo()
            )
            EventStreamUploader(factory, aws, moshi, options)
                .upload()
                .collect { result: UploadResult ->
                    println("uploaded #${result.batchId} => S3 ETag: ${result.eTag}")
                }
        }
    }
}
