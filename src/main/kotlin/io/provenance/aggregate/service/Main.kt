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
import io.provenance.aggregate.service.stream.EventStream
import io.provenance.aggregate.service.stream.EventStreamUploader
import io.provenance.aggregate.service.stream.TendermintServiceClient
import io.provenance.aggregate.service.stream.json.JSONObjectAdapter
import kotlinx.coroutines.Dispatchers
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

fun main(args: Array<String>) {
    // All configuration options can be overridden via environment variables:
    // - ENVIRONMENT=development will override the application.properties value "environment=local".
    // - To override nested configuration options separated with a dot ("."), use double underscores ("__")
    //  in the environment variable:
    //    event.stream.rpc_uri=http://localhost:26657 is overridden by "event__stream_rpc_uri=foo"
    //
    // See https://github.com/sksamuel/hoplite#environmentvariablespropertysource
    val config: Config = ConfigLoader.Builder()
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .addSource(PropertySource.resource("/application.properties"))
        .build()
        .loadConfig<Config>()
        .getUnsafe()

    val log = object {}.logger()

    val lastHeight: Long? = args.firstOrNull()?.let { it.toLongOrNull() }
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()
    val wsStreamBuilder = configureEventStreamBuilder(config.event.stream.websocketUri)
    val tendermintService = TendermintServiceClient(config.event.stream.rpcUri)
    val factory = EventStream.Factory(config, moshi, wsStreamBuilder, tendermintService)
    val aws: AwsInterface = AwsInterface.create(config.environment, config.s3)

    runBlocking(Dispatchers.IO) {

        EventStreamUploader(factory, aws, moshi, lastHeight, skipEmptyBlocks = true)
            .upload()

//        EventStreamConsumer(factory, lastHeight, skipEmptyBlocks = true)
//            .consume { b: StreamBlock, serialize: (StreamBlock) -> String ->
//                //println(serialize(b))
//                val text = "BLOCK = ${b.block.header?.height ?: "--"}:${b.block.header?.dateTime()?.toLocalDate()}"
//                log.info(
//                    if (b.historical) {
//                        text
//                    } else {
//                        green(text)
//                    }
//                )
//            }
    }
}
