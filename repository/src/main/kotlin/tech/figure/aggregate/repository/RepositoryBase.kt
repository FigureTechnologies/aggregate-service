package tech.figure.aggregate.repository

import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.repository.database.dynamo.WriteResult


interface RepositoryBase {
    suspend fun saveBlock(block: StreamBlock)

    suspend fun writeBlockCheckpoint(blockHeight: Long): WriteResult

    suspend fun getBlockCheckpoint(): Long?
}
