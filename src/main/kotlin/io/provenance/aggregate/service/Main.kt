package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.aggregate.service.aws.AwsInterface
import io.provenance.aggregate.service.stream.*
import io.provenance.aggregate.service.stream.json.JSONObjectAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
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
     val config: Config = ConfigLoader.Builder()
        .addSource(PropertySource.resource("/application.properties"))
        .build()
        .loadConfig<Config>()
        .getUnsafe()

    if(config.environment == "development") {
        AwsInterface().startLocalstackContainer()
    }

    val log = object{}.logger()

    val lastHeight: Long? = args.firstOrNull()?.let { it.toLongOrNull() }
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()
    val wsStreamBuilder = configureEventStreamBuilder(config.event.stream.websocket_uri)
    val tendermintService = TendermintServiceClient(config.event.stream.rpc_uri)
    val factory = EventStream.Factory(config, moshi, wsStreamBuilder, tendermintService)

    runBlocking(Dispatchers.Default) {
        EventStreamConsumer(factory, lastHeight, skipEmptyBlocks = true)
            .consume { b: StreamBlock, serialize: (StreamBlock) -> String ->
                //println(serialize(b))
                log.info(
                    "BLOCK = ${b.block.header?.height ?: "--"}:${b.block.header?.dataHash} (${
                        if (b.historical) {
                            "historical"
                        } else {
                            "live"
                        }
                    })"
                )
            }
    }
}
