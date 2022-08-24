package tech.figure.aggregate.repository.model

import tech.figure.aggregate.common.decodeBase64
import tech.figure.aggregate.common.models.block.StreamBlock
import io.provenance.eventstream.stream.models.Event

fun StreamBlock.txHash(index: Int): String? = this.block.data?.txs?.get(index)

fun List<Event>.toDecodedAttributes(): List<EventData> =
    this.map { EventData(it.key?.decodeBase64(), it.value?.decodeBase64(), it.index ?: false) }


