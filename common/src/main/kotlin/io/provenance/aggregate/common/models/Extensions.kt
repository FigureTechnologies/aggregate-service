package io.provenance.aggregate.common.models.extensions

import io.provenance.aggregate.common.extensions.decodeBase64
import io.provenance.aggregate.common.extensions.hash
import io.provenance.aggregate.common.models.*
import tendermint.types.Types.Header as GrpcHeader
import java.time.Instant
import tendermint.types.BlockOuterClass.Block as GrpcBlock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// === RPC ========================================================================================================

fun Block.txHash(index: Int): String? = this.data?.txs?.get(index)?.hash()

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun Block.dateTime() = this.header?.dateTime()

fun BlockHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this.time, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

fun BlockResponse.txHash(index: Int): String? = this.result?.block?.txHash(index)

fun BlockResultsResponse.txEvents(blockDate: OffsetDateTime, gasPrice: Double, txHash: (index: Int) -> String): List<TxEvent> =
    this.result.txEvents(blockDate, gasPrice, txHash)

fun BlockResultsResponseResult.txEvents(blockDateTime: OffsetDateTime?, gasPrice: Double, txHash: (Int) -> String): List<TxEvent> =
    run {
        txsResults?.flatMapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
            tx.events
                ?.map {
                    it.toTxEvent(
                        height,
                        blockDateTime,
                        txHash(index),
                        (tx.gasWanted?.toLong()?.times(gasPrice))
                    )
                }
                ?: emptyList()
        }
    } ?: emptyList()

fun BlockResultsResponseResult.txErroredEvents(blockDateTime: OffsetDateTime?, gasPrice: Double): List<TxError> {
    val txErrors = mutableListOf<TxError>()
    txsResults?.map {
        if(it.code?.toInt() != 0) {
            txErrors.add(
                it.toBlockError(
                    blockHeight = height,
                    blockDateTime = blockDateTime,
                    fee = (it.gasWanted?.toLong()?.times(gasPrice))
                )
            )
        }
    }
    return txErrors
}

fun BlockResultsResponseResultTxsResults.toBlockError(blockHeight: Long, blockDateTime: OffsetDateTime?, fee: Double?): TxError =
    TxError(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        code = this.code!!.toLong(),
        info = this.log ?: "",
        fee = fee?.toLong()
    )

fun BlockResultsResponseResult.blockEvents(blockDateTime: OffsetDateTime?): List<BlockEvent> = run {
    beginBlockEvents?.map { e: BlockResultsResponseResultEvents ->
        BlockEvent(
            blockHeight = height,
            blockDateTime = blockDateTime,
            eventType = e.type ?: "",
            attributes = e.attributes ?: emptyList()
        )
    }
} ?: emptyList()

fun BlockResultsResponseResultEvents.toBlockEvent(blockHeight: Long, blockDateTime: OffsetDateTime?): BlockEvent =
    BlockEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList()
    )

fun BlockResultsResponseResultEvents.toTxEvent(
    blockHeight: Long,
    blockDateTime: OffsetDateTime?,
    txHash: String,
    fee: Double?
): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash,
        fee = fee?.toLong(),
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList()
    )

/**
 * A utility function which converts a list of key/value event attributes like:
 *
 *   [
 *     {
 *       "key": "cmVjb3JkX2FkZHI=",
 *       "value": "InJlY29yZDFxMm0zeGFneDc2dXl2ZzRrN3l2eGM3dWhudWdnOWc2bjBsY2Robm43YXM2YWQ4a3U4Z3ZmdXVnZjZ0aiI="
 *     },
 *     {
 *       "key": "c2Vzc2lvbl9hZGRy",
 *       "value": "InNlc3Npb24xcXhtM3hhZ3g3NnV5dmc0azd5dnhjN3VobnVnMHpwdjl1cTNhdTMzMmsyNzY2NmplMGFxZ2o4Mmt3dWUi"
 *     },
 *     {
 *       "key": "c2NvcGVfYWRkcg==",
 *       "value": "InNjb3BlMXF6bTN4YWd4NzZ1eXZnNGs3eXZ4Yzd1aG51Z3F6ZW1tbTci"
 *     }
 *   ]
 *
 * which have been deserialized in `List<Event>`, into `Map<String, String>`,
 *
 * where keys have been base64 decoded:
 *
 *   {
 *     "record_addr"  to "InJlY29yZDFxMm0zeGFneDc2dXl2ZzRrN3l2eGM3dWhudWdnOWc2bjBsY2Robm43YXM2YWQ4a3U4Z3ZmdXVnZjZ0aiI=",
 *     "session_addr" to "InNlc3Npb24xcXhtM3hhZ3g3NnV5dmc0azd5dnhjN3VobnVnMHpwdjl1cTNhdTMzMmsyNzY2NmplMGFxZ2o4Mmt3dWUi",
 *     "scope_addr"   to "InNjb3BlMXF6bTN4YWd4NzZ1eXZnNGs3eXZ4Yzd1aG51Z3F6ZW1tbTci"
 *   }
 */
fun List<Event>.toDecodedMap(): Map<String, String?> =
    this.mapNotNull { e -> e.key?.let { it.decodeBase64() to e.value } }
        .toMap()

// === gRPC ========================================================================================================

fun GrpcBlock.dateTime() = this.header.dateTime()

fun GrpcHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.ofInstant(Instant.ofEpochSecond(this.time.seconds), ZoneOffset.UTC) }.getOrNull()
