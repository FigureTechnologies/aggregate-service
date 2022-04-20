package io.provenance.aggregate.repository.model

import io.provenance.aggregate.common.extensions.decodeBase64
import io.provenance.aggregate.common.models.Event
import io.provenance.aggregate.common.models.StreamBlock

fun StreamBlock.txHash(index: Int): String? = this.block.data?.txs?.get(index)

fun List<Event>.toDecodedAttributes(): List<EventData> =
    this.map { EventData(it.key?.decodeBase64(), it.value?.decodeBase64(), it.index ?: false) }



