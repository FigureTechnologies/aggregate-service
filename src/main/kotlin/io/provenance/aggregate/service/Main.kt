package io.provenance.aggregate.service

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.aggregate.service.stream.EventStreamConsumer
import io.provenance.aggregate.service.stream.EventStreamFactory
import io.provenance.aggregate.service.stream.StreamBlock
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

    val wsStreamBuilder = configureEventStreamBuilder(config.event.stream.websocket_uri)

    val eventStream = EventStreamFactory(config, wsStreamBuilder)

    val lastHeight: Long? = args.firstOrNull()?.let { it.toLongOrNull() }

    runBlocking(Dispatchers.Default) {
        EventStreamConsumer(eventStream, lastHeight, skipEmptyBlocks = true)
            .consume { b: StreamBlock ->
                println(
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
