package io.provenance.aggregate.common.models.extensions

import com.google.common.io.BaseEncoding
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.aggregate.common.extensions.decodeBase64
import io.provenance.aggregate.common.extensions.hash
import io.provenance.aggregate.common.models.*
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.hdwallet.bech32.toBech32
import io.provenance.hdwallet.common.hashing.sha256hash160
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import tendermint.types.Types.Header as GrpcHeader
import java.time.Instant
import tendermint.types.BlockOuterClass.Block as GrpcBlock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// === RPC ========================================================================================================


/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded byte array.
 */
fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded string.
 *
 * @param input An array of bytes.
 * @return An array of SHA-256 hashed bytes.
 */
fun sha256(input: ByteArray?): ByteArray =
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(input)
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("Couldn't find a SHA-256 provider", e)
    }

/**
 * Compute a hex-encoded (printable) SHA-256 encoded string, from a base64 encoded string.
 */
fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

// === Date/time methods ===============================================================================================

fun io.provenance.eventstream.stream.models.Block.txData(index: Int): TxInfo? {
    val tx = this.data?.txs?.get(index)

    if (tx != null) {
        val feeInfo = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(tx)).authInfo.fee
        val amount = feeInfo.amountList.getOrNull(0)?.amount?.toLong()
        val denom = feeInfo.amountList.getOrNull(0)?.denom
        return TxInfo(
            this.data?.txs?.get(index)?.hash(),
            Pair(amount, denom)
        )
    }
    return null
}

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun io.provenance.eventstream.stream.models.Block.dateTime() = this.header?.dateTime()

fun io.provenance.eventstream.stream.models.BlockHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this.time, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

fun io.provenance.eventstream.stream.models.BlockResultsResponseResult.txEvents(blockDateTime: OffsetDateTime?, txHash: (Int) -> TxInfo?): List<TxEvent> =
    run {
        txsResults?.flatMapIndexed { index: Int, tx: io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults ->
            tx.events
                ?.map { it.toTxEvent(height, blockDateTime, txHash(index)?.txHash, txHash(index)?.fee) }
                ?: emptyList()
        }
    } ?: emptyList()

fun String.toSignerAddr(): List<String> {
    val tx = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(this)) ?: return mutableListOf()
    return tx.authInfo.signerInfosList.map {
        Keys.PubKey.parseFrom(it.publicKey.value).key.toByteArray().sha256hash160().toBech32("tp").address.value
    }
}

fun io.provenance.eventstream.stream.models.BlockResultsResponseResult.txErroredEvents(block: io.provenance.eventstream.stream.models.Block, blockDateTime: OffsetDateTime?, txHash: (Int) -> TxInfo?): List<TxError> =
    run {
        txsResults?.mapIndexed { index: Int, tx: io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults ->
            if (tx.code?.toInt() != 0) {
                val signerAddr = block.data?.txs?.get(index)?.toSignerAddr() ?: mutableListOf()
                tx.toBlockError(height, blockDateTime, txHash(index)?.txHash, txHash(index)?.fee, signerAddr)
            } else {
                null
            }
        }?.filterNotNull()
    } ?: emptyList()

fun io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults.toBlockError(blockHeight: Long, blockDateTime: OffsetDateTime?, txHash: String?, fee: Pair<Long?, String?>?, signerAddr: List<String>): TxError? =
    TxError(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        code = this.code?.toLong() ?: 0L,
        info = this.log ?: "",
        txHash = txHash ?: "",
        fee = fee?.first ?: 0L,
        signerAddr = signerAddr,
        denom = fee?.second ?: ""
    )

fun io.provenance.eventstream.stream.models.BlockResultsResponseResultEvents.toTxEvent(
    blockHeight: Long,
    blockDateTime: OffsetDateTime?,
    txHash: String?,
    fee: Pair<Long?, String?>?
): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash ?: "",
        eventType = this.type ?: "",
        attributes = this.attributes?.map { event -> Event(event.key,event.value, event.index) } ?: emptyList(),
        fee = fee?.first,
        denom = fee?.second
    )

fun io.provenance.eventstream.stream.models.BlockResultsResponse.txEvents(blockDate: OffsetDateTime, txHash: (index: Int) -> TxInfo): List<TxEvent> =
    this.result.txEvents(blockDate, txHash)

fun io.provenance.eventstream.stream.models.BlockResultsResponseResult.blockEvents(blockDateTime: OffsetDateTime?): List<BlockEvent> = run {
    beginBlockEvents?.map { e: io.provenance.eventstream.stream.models.BlockResultsResponseResultEvents ->
        BlockEvent(
            blockHeight = height,
            blockDateTime = blockDateTime,
            eventType = e.type ?: "",
            attributes = e.attributes?.map { event -> Event(event.key,event.value, event.index) } ?: emptyList()
        )
    }
} ?: emptyList()

fun BlockData.toStreamBlock(): StreamBlockImpl {
    val blockDatetime = block.header?.dateTime()
    val blockEvents = blockResult.blockEvents(blockDatetime)
    val blockTxResults = blockResult.txsResults
    val txEvents = blockResult.txEvents(blockDatetime) { index: Int -> block.txData(index) }
    val txErrors = blockResult.txErroredEvents(block, blockDatetime) { index: Int -> block.txData(index) }
    return StreamBlockImpl(block, blockEvents, blockTxResults, txEvents, txErrors)
}


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
