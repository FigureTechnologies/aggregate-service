package io.provenance.aggregate.service.stream.extensions

import io.provenance.aggregate.service.stream.BlockEvent
import io.provenance.aggregate.service.stream.TxEvent
import io.provenance.aggregate.service.stream.models.*
import com.google.common.io.BaseEncoding
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

private fun ByteArray.toHexString() = BaseEncoding.base16().encode(this)

private fun sha256(input: ByteArray?): ByteArray =
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(input)
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("Couldn't find a SHA-256 provider", e)
    }

fun Block.txHash(index: Int): String? = this.data?.txs?.get(index)?.hash()

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun BlockResponse.txHash(index: Int): String? = this.result?.block?.txHash(index)

fun BlockResultsResponse.txEvents(txHash: (index: Int) -> String): List<TxEvent> = this.result.txEvents(txHash)

fun BlockResultsResponseResult.txEvents(txHash: (Int) -> String): List<TxEvent> =
    this.let {
        val blockHeight = it.height
        it.txsResults?.flatMapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
            tx.events
                ?.map { it.toTxEvent(blockHeight, txHash(index)) }
                ?: emptyList()
        }
    } ?: emptyList()

fun BlockResultsResponseResult.blockEvents(): List<BlockEvent> = this.let {
    it.beginBlockEvents?.map { e: BlockResultsResponseResultEvents ->
        BlockEvent(
            height = it.height,
            eventType = e.type ?: "",
            attributes = e.attributes ?: emptyList()
        )
    }
} ?: emptyList()

fun BlockResultsResponseResultEvents.toBlockEvent(height: Long): BlockEvent =
    BlockEvent(
        height = height,
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList()
    )

fun BlockResultsResponseResultEvents.toTxEvent(height: Long, txHash: String): TxEvent =
    TxEvent(
        height = height,
        txHash = txHash,
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList()
    )
