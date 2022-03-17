package io.provenance.aggregate.service.stream

import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.aggregate.common.Config
import io.provenance.aggregate.common.aws.dynamodb.client.DynamoClient
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.stream.BlockStreamFactory
import io.provenance.eventstream.stream.BlockStreamOptions
import io.provenance.eventstream.stream.Checkpoint
import io.provenance.eventstream.stream.EventStream
import io.provenance.eventstream.stream.FileCheckpoint
import io.provenance.eventstream.stream.TendermintEventStreamService
import io.provenance.eventstream.stream.TendermintRPCStream
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi

class EventStreamFactory(
    private val config: Config,
    private val moshi: DecoderEngine,
    private val eventStreamBuilder: Scarlet.Builder,
    private val fetcher: TendermintBlockFetcher,
//    private val dynamoClient: DynamoClient,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val checkpoint: Checkpoint = FileCheckpoint()
): BlockStreamFactory {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createSource(options: BlockStreamOptions): BlockSource<StreamBlockImpl> {
        val lifecycle = LifecycleRegistry(config.eventStream.websocket.throttleDurationMs)
        val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
        val tendermintRpc: TendermintRPCStream = scarlet.create(TendermintRPCStream::class.java)
        val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)
//        val feeCollector = config.feeCollector
//        val dynamoBatchGetItems = config.aws.dynamodb.dynamoBatchGetItems

        return EventStream(
            eventStreamService,
            fetcher,
            moshi,
            dispatchers,
            checkpoint,
            options
        )
    }
}
