package io.provenance.aggregate.service.stream

import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.common.Config
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.stream.BlockStreamFactory
import io.provenance.eventstream.stream.BlockStreamOptions
import io.provenance.eventstream.stream.EventStream
import io.provenance.eventstream.stream.WebSocketChannel
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.withLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

class EventStreamFactory(
    private val config: Config,
    private val decoderEngine: DecoderEngine,
    private val eventStreamBuilder: Scarlet.Builder,
    private val fetcher: TendermintBlockFetcher,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
): BlockStreamFactory {
    private val log = LoggerFactory.getLogger(javaClass)

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    override fun createSource(options: BlockStreamOptions): BlockSource<StreamBlockImpl> {
        log.info("Connecting stream instance to  ${config.wsNode}")
        val lifecycle = LifecycleRegistry(config.eventStream.websocket.throttleDurationMs)
        val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
        val channel: WebSocketChannel = scarlet.create(WebSocketChannel::class.java)
        val eventStreamService = channel.withLifecycle(lifecycle)

        return EventStream(
            eventStreamService,
            fetcher,
            decoderEngine,
            options = options,
            dispatchers = dispatchers
        )
    }
}
