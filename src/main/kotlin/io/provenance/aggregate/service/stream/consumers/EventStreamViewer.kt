package io.provenance.aggregate.service.stream.consumers

import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.service.stream.EventStreamLegacy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

/**
 * An event stream consumer that displays blocks from the provided event stream.
 *
 * @property eventStream The event stream which provides blocks to this consumer.
 * @property options Options used to configure this consumer.
 */
@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStreamViewer(
    private val eventStream: EventStreamLegacy,
    private val options: EventStreamLegacy.Options = EventStreamLegacy.Options.DEFAULT
) {
    constructor(
        eventStreamFactory: EventStreamLegacy.Factory,
        options: EventStreamLegacy.Options = EventStreamLegacy.Options.DEFAULT
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
        eventStream.streamBlocks()
            .buffer()
            .catch { error(it) }
            .collect { ok(it, eventStream.serializer) }
    }
}
