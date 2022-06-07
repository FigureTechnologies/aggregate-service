package io.provenance.aggregate.repository

import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.repository.database.dynamo.WriteResult


interface RepositoryBase {
    suspend fun saveBlock(block: StreamBlock)

    suspend fun writeBlockCheckpoint(blockHeight: Long): WriteResult

    suspend fun getBlockCheckpoint(): Long?
}
