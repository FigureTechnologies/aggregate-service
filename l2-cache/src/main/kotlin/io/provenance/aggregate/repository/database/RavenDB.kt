package io.provenance.aggregate.repository.database

import io.provenance.aggregate.common.extensions.toHexString
import io.provenance.aggregate.common.models.BlockResultsResponseResultTxsResults
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.models.TxEvent
import io.provenance.aggregate.common.utils.sha256
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.model.BlockMetadata
import io.provenance.aggregate.repository.model.Tx
import io.provenance.aggregate.repository.model.TxEvents
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession
import java.util.UUID

class RavenDB(addr: String?, dbName: String?): RepositoryBase<Any> {

    private val store = DocumentStore(addr, dbName).also { it.initialize() }
    private var session: IDocumentSession = store.openSession()

    override fun saveBlockMetadata(block: StreamBlock) =
        session.store(blockMetadata(block), sha256(UUID.randomUUID().toString()).toHexString())

    override fun saveBlockTx(blockHeight: Long?, blockTxResult: List<BlockResultsResponseResultTxsResults>?, txHash: (Int) -> String?) {
        blockTx(blockHeight, blockTxResult, txHash).map {
            session.store(it)
        }
    }

    override fun saveBlockTxEvents(blockHeight: Long?, txEvents: List<TxEvent>?) {
        blockTxEvent(blockHeight, txEvents).map {
            session.store(it)
        }
    }

    override fun saveChanges() {
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
