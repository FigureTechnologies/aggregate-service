package io.provenance.aggregate.service.stream

import arrow.core.Either
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect

class EventStreamConsumer(
    private val eventStreamFactory: EventStream.Factory,
    private val lastHeight: Long?,
    private val skipEmptyBlocks: Boolean = true
) {
    private val log = logger()

    private fun onError(error: Throwable) {
        log.error("$error")
    }

    suspend fun consume(error: (Throwable) -> Unit = ::onError, ok: (block: StreamBlock) -> Unit) {
        consume(error) { b, _ -> ok(b) }
    }

    suspend fun consume(
        error: (Throwable) -> Unit = ::onError,
        ok: (block: StreamBlock, serialize: (StreamBlock) -> String) -> Unit
    ) {
        if (lastHeight != null) {
            if (lastHeight < 0) {
                throw IllegalArgumentException("lastHeight must be greater than 0")
            }
            log.info("Starting event stream at height $lastHeight")
        }

        val eventStream = eventStreamFactory.getStream(skipEmptyBlocks)
        val serializer = { b: StreamBlock -> eventStream.serialize(StreamBlock::class.java, b) }

        eventStream.streamBlocks(lastHeight)
            .buffer(1000)
            .collect {
                when (it) {
                    is Either.Left -> error(it.value)
                    is Either.Right -> ok(it.value, serializer)
                }
            }
    }
}
