package io.provenance.aggregate.service.stream.models.extensions

import io.provenance.aggregate.service.extensions.hash
import io.provenance.aggregate.service.stream.BlockEvent
import io.provenance.aggregate.service.stream.RpcResponse
import io.provenance.aggregate.service.stream.TxEvent
import io.provenance.aggregate.service.stream.models.*
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun RpcResponse<JSONObject>.isEmpty(): Boolean = this.result?.isEmpty ?: true

fun Block.txHash(index: Int): String? = this.data?.txs?.get(index)?.hash()

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun Block.dateTime() = this.header?.dateTime()

fun BlockHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this.time, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

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
