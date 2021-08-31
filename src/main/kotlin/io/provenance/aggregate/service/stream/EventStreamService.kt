package io.provenance.aggregate.service.stream

import com.squareup.moshi.JsonClass
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import io.provenance.aggregate.service.stream.models.Block
import io.provenance.aggregate.service.stream.models.BlockResultsResponseResultEvents
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

@JsonClass(generateAdapter = true)
data class Subscribe(
    val jsonrpc: String = "2.0",
    val id: String = "0",
    val method: String = "subscribe",
    val params: SubscribeParams
) {
    constructor(query: String) : this(params = SubscribeParams(query))
}

@JsonClass(generateAdapter = true)
open class RpcRequest(val method: String, val params: Any? = null) {
    open val jsonrpc: String = "2.0"
    open val id: String = "0"
}

@JsonClass(generateAdapter = true)
data class RpcResponse<T>(
    val jsonrpc: String,
    val id: String,
    val result: T? = null,
    val error: RpcError? = null
)

@JsonClass(generateAdapter = true)
data class RpcError(
    val code: Int,
    val message: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class SubscribeParams(
    val query: String
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
     * Allow the websocket event flow to start receiving events.
     *
     * Note: this must be called prior to any
     */
    override fun startListening() {
        lifecycle.onNext(Lifecycle.State.Started)
    }

    /**
     * Stops the websocket event flow from receiving events.
     */
    override fun stopListening() {
        lifecycle.onNext(Lifecycle.State.Stopped.AndAborted)
    }
}