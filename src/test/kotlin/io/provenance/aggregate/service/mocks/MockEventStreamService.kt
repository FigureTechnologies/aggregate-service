package io.provenance.aggregate.service.mocks

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.EventStreamService
import io.provenance.aggregate.service.stream.Subscribe
import io.provenance.aggregate.service.utils.Defaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runBlockingTest
import java.util.concurrent.atomic.AtomicLong

class MockEventStreamService private constructor(
    private val channel: Channel<WebSocket.Event>,
    private val responseCount: Long,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider
) : EventStreamService {

    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var dispatchers: DispatcherProvider? = null
        private var moshi: Moshi = Defaults.moshi
        private val payloads = mutableListOf<String>()

        // setters:

        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }
        fun moshi(value: Moshi) = apply { moshi = value }

        fun response(vararg jsonData: String): Builder = apply {
            for (json in jsonData) {
                payloads.add(json)
            }
        }

        fun <T> response(clazz: Class<T>, vararg eventData: T): Builder = apply {
            val response: JsonAdapter<T> = moshi.adapter(clazz)
            for (datum in eventData) {
                payloads.add(response.toJson(datum))
            }
        }

        // build:

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

    fun expectedResponseCount(): Long = responseCount

    // Stubbed out channel that will stop the iterator and subsequent blocking when all the items in the channel
    // have been added via the `response()` builder method have been consumed.
    override fun observeWebSocketEvent(): ReceiveChannel<WebSocket.Event> {
        val iterator = channel.iterator()
        var unconsumedMessageCount: AtomicLong = AtomicLong(responseCount)

        return object : ReceiveChannel<WebSocket.Event> by channel {

            override fun iterator(): ChannelIterator<WebSocket.Event> {

                return object : ChannelIterator<WebSocket.Event> by iterator {

                    override fun next(): WebSocket.Event {
                        unconsumedMessageCount.decrementAndGet()
                        return iterator.next()
                    }

                    override suspend fun hasNext(): Boolean {
                        if (unconsumedMessageCount.get() <= 0) {
                            channel.close()
                            return false
                        }
                        return iterator.hasNext()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribe(subscribe: Subscribe) {
        runBlockingTest(dispatchers.main()) {
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