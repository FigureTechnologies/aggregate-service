package io.provenance.aggregate.repository.database

import io.provenance.aggregate.common.logger
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.model.*
import io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.eventstream.stream.models.extensions.hash
import io.provenance.eventstream.stream.models.extensions.txHashes
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession

class RavenDB(addr: String?, dbName: String?, maxConnections: Int): RepositoryBase {

    private val store = DocumentStore(addr, dbName).also { it.conventions.maxNumberOfRequestsPerSession = maxConnections }.initialize()
    private val log = logger()

    override fun saveBlock(block: StreamBlock) {
        val session = openSession() // open a new session when preparing to save block

        saveBlockMetadata(session, block)
        saveBlockTx(session, block.height, block.blockResult) { index: Int ->  block.block.data?.txs?.get(index)?.hash() }
        saveBlockTxEvents(session, block.height, block.txEvents)

        /**
         * We need to close after save session due to amount of request we can make to RavenDB
         * at a time (100 set) the amount of request degrades RavenDB write performance.
         */
        saveChanges(session) // close session when done saving block data
    }

    private fun openSession(): IDocumentSession = store.openSession()

    private fun saveBlockMetadata(session: IDocumentSession, block: StreamBlock) =
        session.store(blockMetadata(block), (block.height ?: 0 ).toString())
            .also {
                log.info(" Storing data for Block Height = ${block.height}: Block Result Size = ${block.blockResult?.size}: Tx Event Size = ${block.txEvents.size}")
            }

    private fun saveBlockTx(
        session: IDocumentSession,
        blockHeight: Long?,
        blockTxResult: List<BlockResultsResponseResultTxsResults>?,
        txHash: (Int) -> String?
    ) =
        blockTx(blockHeight, blockTxResult, txHash).map { tx ->
            session.store(tx, tx.txHash)
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
            txHash = block.block.txHashes(),
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
                txHash = txHash(index),
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

        txEvents?.map { event ->
            TxEvents(
                txHash = event.txHash,
                blockHeight = blockHeight,
                eventType = event.eventType,
                attributes = event.attributes.toDecodedAttributes()
            )
        } ?: emptyList()
}
