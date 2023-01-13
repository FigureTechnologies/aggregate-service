package tech.figure.aggregate.repository.database

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.repository.RepositoryBase
import tech.figure.aggregate.repository.model.checkpoint.BlockHeightCheckpoint
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession
import tech.figure.aggregate.common.DBConfig

open class RavenDB(dbConfig: DBConfig): RepositoryBase {

    companion object {
        const val CHECKPOINT_ID = "BlockHeightCheckpoint"
    }

    private val store = DocumentStore(dbConfig.addr, dbConfig.dbName).also { it.conventions.maxNumberOfRequestsPerSession = dbConfig.dbMaxConnections }.initialize()
    private val log = logger()

    override suspend fun writeBlockCheckpoint(blockHeight: Long) {
        val session = openSession()
        val height = session.load(BlockHeightCheckpoint::class.java, CHECKPOINT_ID)
        if(height == null) {
            val checkpoint = BlockHeightCheckpoint(
                blockHeight = blockHeight,
            )
            session.store(checkpoint, CHECKPOINT_ID)
        } else {
            height.blockHeight = blockHeight
        }
        saveChanges(session)
    }

    override suspend fun getBlockCheckpoint(): Long? = openSession().load(BlockHeightCheckpoint::class.java, CHECKPOINT_ID)?.blockHeight

    protected fun openSession(): IDocumentSession {
        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        store.conventions.entityMapper = mapper
        return store.openSession()
    }

    protected fun saveChanges(session: IDocumentSession) {
        session.saveChanges()
        session.close()
    }
}
