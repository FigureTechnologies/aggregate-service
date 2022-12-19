package tech.figure.aggregate.repository.model

import tech.figure.aggregate.common.decodeBase64
import io.provenance.eventstream.stream.models.Event

fun List<Event>.toDecodedAttributes(): List<EventData> =
    this.map { EventData(it.key?.decodeBase64(), it.value?.decodeBase64(), it.index ?: false) }


