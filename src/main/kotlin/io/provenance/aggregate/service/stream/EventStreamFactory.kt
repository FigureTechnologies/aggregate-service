package io.provenance.aggregate.service.stream

import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.common.Config
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.decoder.DecoderAdapter
import io.provenance.eventstream.defaultLifecycle
import io.provenance.eventstream.defaultWebSocketChannel
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.stream.BlockStreamFactory
import io.provenance.eventstream.stream.BlockStreamOptions
import io.provenance.eventstream.stream.Checkpoint
import io.provenance.eventstream.stream.EventStream
import io.provenance.eventstream.stream.FileCheckpoint
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.withLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class EventStreamFactory(
    private val config: Config,
    private val moshiNetAdapter: NetAdapter,
    private val moshiDecoderAdapter: DecoderAdapter,
    private val fetcher: TendermintBlockFetcher,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val checkpoint: Checkpoint = FileCheckpoint()
): BlockStreamFactory {

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    override fun createSource(options: BlockStreamOptions): BlockSource<StreamBlockImpl> {
        val throttle = config.eventStream.websocket.throttleDurationMs.milliseconds
        val lifecycle = defaultLifecycle(throttle)
        val webSocketService = defaultWebSocketChannel(
            moshiNetAdapter.wsAdapter,
            moshiDecoderAdapter.wsDecoder,
            throttle,
            lifecycle
        ).withLifecycle(lifecycle)

        return EventStream(
            webSocketService,
            fetcher,
            moshiDecoderAdapter.decoderEngine,
            dispatchers,
            checkpoint,
            options
        )
    }
}
