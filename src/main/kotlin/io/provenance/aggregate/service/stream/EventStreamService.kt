package io.provenance.aggregate.service.stream

import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import kotlinx.coroutines.channels.ReceiveChannel
import io.provenance.aggregate.service.stream.Subscribe

interface EventStreamService {

    @Receive
    fun observeWebSocketEvent(): ReceiveChannel<WebSocket.Event>

    @Send
    fun subscribe(subscribe: Subscribe)

    // Note: this is a known bug with the scarlet corountine funcitonality.
    // After consuming raw websocket events from observeWebSocketEvent() is called,
    // will not produce any instances of streamEvents() RpcResponse<Result>.
    //
    // https://github.com/Tinder/Scarlet/issues/150
    //
    // @Receive
    // fun streamEvents(): ReceiveChannel<RpcResponse<Result>>
}
