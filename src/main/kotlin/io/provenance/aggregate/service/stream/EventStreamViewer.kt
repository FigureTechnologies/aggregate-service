package io.provenance.aggregate.service.stream

import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.models.StreamBlock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStreamViewer(
    private val eventStream: EventStream,
    private val options: EventStream.Options = EventStream.Options.DEFAULT
) {
    constructor(
        eventStreamFactory: EventStream.Factory,
        options: EventStream.Options = EventStream.Options.DEFAULT
    ) : this(eventStreamFactory.create(options), options)

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
        if (options.fromHeight != null) {
            if (options.fromHeight < 0) {
                throw IllegalArgumentException("lastHeight must be greater than 0")
            }
            log.info("Starting event stream at height ${options.fromHeight}")
        }

        val serializer = { b: StreamBlock -> eventStream.serialize(StreamBlock::class.java, b) }

        eventStream.streamBlocks()
            .buffer()
            .catch { error(it) }
            .collect { ok(it, serializer) }
    }
}
