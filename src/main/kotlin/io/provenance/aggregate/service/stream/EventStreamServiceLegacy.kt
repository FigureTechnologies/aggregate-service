package io.provenance.aggregate.service.stream

import com.squareup.moshi.JsonClass
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import io.provenance.aggregate.common.models.Block
import io.provenance.aggregate.common.models.BlockResultsResponseResultEvents
import io.provenance.aggregate.service.stream.models.rpc.request.Subscribe
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockResult(
    val query: String?,
    val data: NewBlockEventResultData
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultData(
    val type: String,
    val value: NewBlockEventResultValue
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultBeginBlock(
    val events: List<BlockResultsResponseResultEvents>
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultValue(
    val block: Block,
    val result_begin_block: NewBlockEventResultBeginBlock
)

/**
 * Used by the Scarlet library to instantiate an implementation that provides access to a
 * `ReceiveChannel<WebSocket.Event>` that can be used to listen for web socket events.
 */
interface TendermintRPCStreamLegacy {

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

interface EventStreamServiceLegacy : TendermintRPCStreamLegacy {
    /**
     * Starts the stream
     */
    fun startListening()

    /**
     * Stops the stream
     */
    fun stopListening()
}

/**
 * A service that emits realtime block data from the the Tendermint RPC API websocket.
 *
 * @property rpcStream The Tendermint RPC API websocket stream provider (powered by Scarlet).
 * @property lifecycle The lifecycle responsible for starting and stopping the underlying websocket event stream.
 */
class TendermintEventStreamLegacyServiceLegacy(rpcStream: TendermintRPCStreamLegacy, val lifecycle: LifecycleRegistry) :
    TendermintRPCStreamLegacy by rpcStream, EventStreamServiceLegacy {
    /**
     * Allows the provided event stream to start receiving events.
     *
     * Note: this must be called prior to any receiving any events on the RPC stream.
     */
    override fun startListening() {
        lifecycle.onNext(Lifecycle.State.Started)
    }

    /**
     * Stops the provided event stream from receiving events.
     */
    override fun stopListening() {
        lifecycle.onNext(Lifecycle.State.Stopped.AndAborted)
    }
}
