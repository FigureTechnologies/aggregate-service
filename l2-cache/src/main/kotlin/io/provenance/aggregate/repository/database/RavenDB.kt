package io.provenance.aggregate.repository.database

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.provenance.aggregate.common.extensions.toHexString
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.utils.sha256
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.model.BlockMetadata
import io.provenance.aggregate.repository.model.Tx
import io.provenance.aggregate.repository.model.TxEvents
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession
import java.util.UUID

class RavenDB(
    private val addr: String?,
    private val dbName: String?
): RepositoryBase<Any> {

    val store = DocumentStore(addr, dbName).also { it.initialize() }
    val session: IDocumentSession = store.openSession()
    val kMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    override fun save(block: StreamBlock) {

        val blockMeta = BlockMetadata(
            blockHeight = block.height,
            txHash = block.block.data?.txs,
            timestamp = block.block.header?.time,
            numTxs = block.txEvents.size.toLong()
        )

        block.blockResult?.map { blockTxResult ->
            block.txEvents.map { txEvent ->
                val tx = Tx(
                    txHash = txEvent.txHash,
                    code = blockTxResult.code?.toLong(),
                    data = blockTxResult.data,
                    log = blockTxResult.log,
                    info = blockTxResult.info,
                    gasWanted = blockTxResult.gasWanted?.toLong(),
                    gasUsed = blockTxResult.gasUsed?.toLong(),
                    numEvents = block.txEvents.size.toLong()
                )

                val txEvents = TxEvents(
                    txHash = txEvent.txHash,
                    eventType = txEvent.eventType,
                    attributes = txEvent.attributes
                )

                session.store(blockMeta, sha256(UUID.randomUUID().toString()).toHexString())
                session.store(tx)
                session.store(txEvents, sha256(UUID.randomUUID().toString()).toHexString())
            }
        }
    }

    override fun saveChanges() {
        session.saveChanges()
    }
}
