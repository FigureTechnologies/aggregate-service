package io.provenance.aggregate.repository.model

import io.provenance.aggregate.common.models.StreamBlock

fun StreamBlock.txHash(index: Int): String? = this.block.data?.txs?.get(index)


