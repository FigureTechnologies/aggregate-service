package io.provenance.aggregate.service.stream

import com.squareup.moshi.JsonClass
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.BlockResultsResponseResultEvents
import io.provenance.aggregate.service.stream.models.rpc.request.Subscribe
import kotlinx.coroutines.channels.ReceiveChannel

@JsonClass(generateAdapter = true)
data class NewBlockResult(
    val query: String?,
    val data: NewBlockEventResultData
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultData(
    val type: String,
    val value: NewBlockEventResultValue
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultBeginBlock(
    val events: List<BlockResultsResponseResultEvents>
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultValue(
    val block: Block,
    val result_begin_block: NewBlockEventResultBeginBlock
)

interface TendermintRPCStream {

    @Receive
    fun observeWebSocketEvent(): ReceiveChannel<WebSocket.Event>

    @Send
    fun subscribe(subscribe: Subscribe)

    // Note: this is a known bug with the Scarlet coroutine adapter implementation.
    // After consuming raw websocket events emitted from the receiver returned from `observeWebSocketEvent()`,
    // the accompanying RPC response streaming receiver `streamEvents()` will not produce
    // any instances of `RpcResponse<Result>` (basically hanging):
    //
    // See https://github.com/Tinder/Scarlet/issues/150 for details
    //
    // @Receive
    // fun streamEvents(): ReceiveChannel<RpcResponse<Result>>
}

interface EventStreamService : TendermintRPCStream {
    // Start the stream
    fun startListening()

    // Stop the stream
    fun stopListening()
}

class TendermintEventStreamService(rpcStream: TendermintRPCStream, val lifecycle: LifecycleRegistry) :
    TendermintRPCStream by rpcStream, EventStreamService {

    /**
     * Allow the websocket event io.provenance.aggregate.service.flow to start receiving events.
     *
     * Note: this must be called prior to any
     */
    override fun startListening() {
        lifecycle.onNext(Lifecycle.State.Started)
    }

    /**
     * Stops the websocket event io.provenance.aggregate.service.flow from receiving events.
     */
    override fun stopListening() {
        lifecycle.onNext(Lifecycle.State.Stopped.AndAborted)
    }
}