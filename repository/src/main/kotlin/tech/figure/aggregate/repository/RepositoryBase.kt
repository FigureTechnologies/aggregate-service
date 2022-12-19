package tech.figure.aggregate.repository

interface RepositoryBase {
    suspend fun writeBlockCheckpoint(blockHeight: Long)

    suspend fun getBlockCheckpoint(): Long?
}
