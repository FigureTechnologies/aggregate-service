package io.provenance.aggregate.service.stream

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking

class MockEventStreamService private constructor(
    private val channel: Channel<WebSocket.Event>,
    private val responseCount: Int,
    private val moshi: Moshi
) : EventStreamService {

    data class Builder(
        private val moshi: Moshi,
    ) {
        private val payloads = mutableListOf<String>()

        fun addResponse(vararg jsonData: String): Builder = apply {
            for (json in jsonData) {
                payloads.add(json)
            }
        }

        fun <T> addResponse(clazz: Class<T>, vararg eventData: T): Builder = apply {
            val response: JsonAdapter<T> = moshi.adapter(clazz)
            for (datum in eventData) {
                payloads.add(response.toJson(datum))
            }
        }

        suspend fun build(): MockEventStreamService {
            val channel = Channel<WebSocket.Event>(payloads.size)
            for (payload in payloads) {
                channel.send(WebSocket.Event.OnMessageReceived(Message.Text(payload)))
            }
            return MockEventStreamService(channel, payloads.size, moshi)
        }
    }

    fun expectedResponseCount(): Int = responseCount

    override fun observeWebSocketEvent(): ReceiveChannel<WebSocket.Event> = channel

    override fun subscribe(subscribe: Subscribe) {
        runBlocking {
            channel.send(WebSocket.Event.OnConnectionOpened(Unit))
        }
    }

    override fun startListening() {
        // no-op
    }

    override fun stopListening() {
        // no-op
    }
}