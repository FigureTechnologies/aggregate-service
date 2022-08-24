package tech.figure.aggregate.repository.database.ravendb

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.common.models.tx.TxEvent
import tech.figure.aggregate.repository.RepositoryBase
import tech.figure.aggregate.repository.database.dynamo.WriteResult
import tech.figure.aggregate.repository.model.l2cache.BlockMetadata
import tech.figure.aggregate.repository.model.l2cache.Tx
import tech.figure.aggregate.repository.model.checkpoint.BlockHeightCheckpoint
import tech.figure.aggregate.repository.model.toDecodedAttributes
import io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.provenance.eventstream.stream.models.extensions.hash
import io.provenance.eventstream.stream.models.extensions.txHashes
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession
import tech.figure.aggregate.repository.model.TxEvents

open class RavenDB(addr: String?, dbName: String?, maxConnections: Int): RepositoryBase {

    companion object {
        const val CHECKPOINT_ID = "BlockHeightCheckpoint"
    }

    private val store = DocumentStore(addr, dbName).also { it.conventions.maxNumberOfRequestsPerSession = maxConnections }.initialize()
    private val log = logger()

    override suspend fun writeBlockCheckpoint(blockHeight: Long): WriteResult {
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

        return WriteResult.empty()
    }

    override suspend fun getBlockCheckpoint(): Long? = openSession().load(BlockHeightCheckpoint::class.java, CHECKPOINT_ID)?.blockHeight

    override suspend fun saveBlock(block: StreamBlock) {
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

    protected fun openSession(): IDocumentSession {
        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        store.conventions.entityMapper = mapper
        return store.openSession()
    }

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

    private fun saveBlockTxEvents(session: IDocumentSession, blockHeight: Long?, txEvents: List<TxEvent>) =
        blockTxEvent(blockHeight, txEvents).map {
            session.store(it)
        }

    protected fun saveChanges(session: IDocumentSession) {
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
