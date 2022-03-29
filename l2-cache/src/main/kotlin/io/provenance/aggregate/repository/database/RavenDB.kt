package io.provenance.aggregate.repository.database

import io.provenance.aggregate.common.extensions.toHexString
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.BlockResultsResponseResultTxsResults
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.models.TxEvent
import io.provenance.aggregate.common.utils.sha256
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.model.BlockMetadata
import io.provenance.aggregate.repository.model.Tx
import io.provenance.aggregate.repository.model.TxEvents
import io.provenance.aggregate.repository.model.txHash
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession
import java.util.UUID

class RavenDB(addr: String?, dbName: String?, maxConnections: Int): RepositoryBase {

    private val store = DocumentStore(addr, dbName).also { it.conventions.maxNumberOfRequestsPerSession = maxConnections }.initialize()
    private val log = logger()

    override fun saveBlock(block: StreamBlock) {
        val session = openSession() // open a new session when preparing to save block

        saveBlockMetadata(session, block)
        saveBlockTx(session, block.height, block.blockResult) { index: Int ->  block.txHash(index) }
        saveBlockTxEvents(session, block.height, block.txEvents)

        /**
         * We need to close after save session due to amount of request we can make to RavenDB
         * at a time (100 set) the amount of request degrades RavenDB write performance.
         */
        saveChanges(session) // close session when done saving block data
    }

    private fun openSession(): IDocumentSession = store.openSession()

    private fun saveBlockMetadata(session: IDocumentSession, block: StreamBlock) =
        session.store(blockMetadata(block), sha256(UUID.randomUUID().toString()).toHexString())
            .also {
                log.info(" Storing data for Block Height = ${block.height}: Block Result Size = ${block.blockResult?.size}: Tx Event Size = ${block.txEvents.size}")
            }

    private fun saveBlockTx(
        session: IDocumentSession,
        blockHeight: Long?,
        blockTxResult: List<BlockResultsResponseResultTxsResults>?,
        txHash: (Int) -> String?
    ) =
        blockTx(blockHeight, blockTxResult, txHash).map {
            session.store(it)
        }

    private fun saveBlockTxEvents(session: IDocumentSession, blockHeight: Long?, txEvents: List<TxEvent>?) =
        blockTxEvent(blockHeight, txEvents).map {
            session.store(it)
        }

    private fun saveChanges(session: IDocumentSession) {
        session.saveChanges()
        session.close()
    }

    private fun blockMetadata(block: StreamBlock): BlockMetadata =
        BlockMetadata(
            blockHeight = block.height,
            txHash = block.block.data?.txs,
            timestamp = block.block.header?.time,
            numTxs = block.txEvents.size.toLong()
        )

    private fun blockTx(
        blockHeight: Long?,
        blockTxResult: List<BlockResultsResponseResultTxsResults>?,
        txHash: (Int) -> String?
    ): List<Tx> =
        blockTxResult?.mapIndexed { index, tx ->
            Tx(
                txHash = sha256(txHash(index)).toHexString(),
                blockHeight = blockHeight,
                code = tx.code?.toLong(),
                data = tx.data,
                log = tx.log,
                info = tx.info,
                gasWanted = tx.gasWanted?.toLong(),
                gasUsed = tx.gasUsed?.toLong(),
                numEvents = tx.events?.size?.toLong()
            )
        } ?: emptyList()

    private fun blockTxEvent(blockHeight: Long?, txEvents: List<TxEvent>?): List<TxEvents> =
        txEvents?.mapIndexed { index, event ->
            TxEvents(
                txHash = event.txHash,
                blockHeight = blockHeight,
                eventType = event.eventType,
                attributes = event.attributes
            )
        } ?: emptyList()
}
