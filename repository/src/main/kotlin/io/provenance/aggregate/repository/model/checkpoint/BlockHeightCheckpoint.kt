package io.provenance.aggregate.repository.model.checkpoint

import io.provenance.aggregate.common.utils.timestamp

data class BlockHeightCheckpoint(
    var timestamp: String = timestamp(),
    var blockHeight: Long
)
