package io.provenance.aggregate.repository.model

import io.provenance.eventstream.stream.models.StreamBlock

fun StreamBlock.txHash(index: Int): String? = this.block.data?.txs?.get(index)


