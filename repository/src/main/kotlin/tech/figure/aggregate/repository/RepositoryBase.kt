package tech.figure.aggregate.repository

import tech.figure.aggregate.common.models.block.StreamBlock

interface RepositoryBase {
    suspend fun saveBlock(block: StreamBlock)

    suspend fun writeBlockCheckpoint(blockHeight: Long)

    suspend fun getBlockCheckpoint(): Long?
}
