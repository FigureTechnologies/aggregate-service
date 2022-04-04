package io.provenance.aggregate.service.test.mocks

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.WebSocket.Event
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.service.test.utils.Defaults
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.WebSocketService
import io.provenance.eventstream.stream.rpc.request.Subscribe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

class MockEventStreamService private constructor(
    private val channel: Channel<WebSocket.Event>,
    private val responseCount: Long,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider
) : WebSocketService {

    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var dispatchers: DispatcherProvider? = null
        private var moshi: Moshi = Defaults.moshi
        private val payloads = mutableListOf<String>()

        /**
         * Sets the dispatchers used by this event stream.
         *
         * @property value The dispatcher provider to use.
         * @return this
         */
        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }

        /**
         * Sets the JSON serializer used by this event stream.
         *
         * @property value The Moshi serializer instance to use.
         * @return this
         */
        fun moshi(value: Moshi) = apply { moshi = value }

        /**
         * Add a response to be emitted by the event stream.
         *
         * @property jsonData A JSON formatted string response for the stream to produce upon collection.
         * @return this
         */
        fun response(vararg jsonData: String): Builder = apply {
            for (json in jsonData) {
                payloads.add(json)
            }
        }

        /**
         * Add a response to be emitted by the event stream.
         *
         * @property clazz The Class defining the type emitted by the event stream.
         * @property eventData The actual data to emit.
         * @return this
         */
        fun <T> response(clazz: Class<T>, vararg eventData: T): Builder = apply {
            val response: JsonAdapter<T> = moshi.adapter(clazz)
            for (datum in eventData) {
                payloads.add(response.toJson(datum))
            }
        }

        /**
         * Creates a new instance of the event stream.
         *
         * @return A mock event stream.
         */
        suspend fun build(): MockEventStreamService {
            val channel = Channel<WebSocket.Event>(payloads.size)
            for (payload in payloads) {
                channel.send(WebSocket.Event.OnMessageReceived(Message.Text(payload)))
            }
            return MockEventStreamService(
                channel = channel,
                responseCount = payloads.size.toLong(),
                moshi = moshi,
                dispatchers = dispatchers ?: error("dispatchers must be provided")
            )
        }
    }

    private val log = logger()

    override fun observeWebSocketEvent(): ReceiveChannel<Event> {
        val iterator = channel.iterator()
        var unconsumedMessageCount = AtomicLong(responseCount)

        return object : ReceiveChannel<Event> by channel {

            override fun iterator(): ChannelIterator<Event> {

                return object : ChannelIterator<Event> by iterator {

                    override fun next(): Event {
                        unconsumedMessageCount.decrementAndGet()
                        return iterator.next()
                    }

                    override suspend fun hasNext(): Boolean {
                        if (unconsumedMessageCount.get() <= 0) {
                            // All messages have been read. We're done:
                            channel.close()
                            stop()
                            return false
                        }
                        return iterator.hasNext()
                    }
                }
            }
        }
    }

    override fun start() {
        // noop
    }

    override fun stop() {
        // noop
    }

    override fun subscribe(subscribe: Subscribe) {
        runBlocking(dispatchers.io()) {
            channel.send(Event.OnConnectionOpened(Unit))
        }
    }

    fun expectedResponseCount(): Long = responseCount
}
