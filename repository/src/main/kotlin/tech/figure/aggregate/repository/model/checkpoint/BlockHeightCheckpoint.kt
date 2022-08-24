package tech.figure.aggregate.repository.model.checkpoint

import tech.figure.aggregate.common.utils.timestamp

data class BlockHeightCheckpoint(
    var timestamp: String = timestamp(),
    var blockHeight: Long
)
