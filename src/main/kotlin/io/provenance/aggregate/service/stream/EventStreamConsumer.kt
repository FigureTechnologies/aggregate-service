package io.provenance.aggregate.service.stream

import arrow.core.Either
import io.provenance.aggregate.service.logger
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect

class EventStreamConsumer(
    private val eventStreamFactory: EventStreamFactory,
    private val lastHeight: Long?,
    private val skipEmptyBlocks: Boolean = true
) {
    private val log = logger()

    private fun onError(error: Throwable) {
        log.error("$error")
    }

    suspend fun consume(error: (Throwable) -> Unit = ::onError, ok: (StreamBlock) -> Unit) {
        if (lastHeight != null) {
            if (lastHeight < 0) {
                throw IllegalArgumentException("lastHeight must be greater than 0")
            }
            log.info("Starting event stream at height $lastHeight")
        }

        eventStreamFactory.getStream(skipEmptyBlocks).streamBlocks(lastHeight)
            .buffer(1000)
            .collect {
                when (it) {
                    is Either.Left -> error(it.value)
                    is Either.Right -> ok(it.value)
                }
            }
    }
}
