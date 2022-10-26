package tech.figure.aggregate.common.models

import com.google.common.io.BaseEncoding
import com.google.gson.Gson
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.v1beta1.TxOuterClass
import tech.figure.aggregate.common.decodeBase64
import tech.figure.aggregate.common.hash
import tech.figure.aggregate.common.models.block.StreamBlockImpl
import tech.figure.aggregate.common.models.block.BlockEvent
import tech.figure.aggregate.common.models.tx.TxError
import tech.figure.aggregate.common.models.tx.TxEvent
import tech.figure.aggregate.common.models.tx.TxInfo
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockHeader
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.provenance.eventstream.stream.models.BlockResultsResponseResultEvents
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.models.Event
import io.provenance.hdwallet.bech32.toBech32
import io.provenance.hdwallet.common.hashing.sha256hash160
import tech.figure.aggregate.common.models.fee.Fee
import tech.figure.aggregate.common.models.fee.SignerInfo
import tech.figure.aggregate.common.repeatDecodeBase64
import java.math.BigDecimal
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

fun Block.txData(index: Int, hrp: String): TxInfo? {
    val tx = this.data?.txs?.get(index)

    if (tx != null) {
        val feeInfo = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(tx)).authInfo.fee
        val amount = feeInfo.amountList.getOrNull(0)?.amount?.toLong()
        val denom = feeInfo.amountList.getOrNull(0)?.denom
        val signerAddr = this.data?.txs?.get(index)?.toSignerAddr(hrp) ?: mutableListOf()

        val feeIncurringAddr = when {
            feeInfo.granter != "" -> feeInfo.granter
            feeInfo.payer != "" -> feeInfo.payer
            else -> signerAddr[0]
        }

        return TxInfo(
            this.data?.txs?.get(index)?.hash(),
            Fee(amount, denom, SignerInfo(signerAddr, feeIncurringAddr))
        )
    }
    return null
}

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun Block.dateTime() = this.header?.dateTime()

fun BlockHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this.time, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

fun BlockResultsResponseResult.txEvents(blockDateTime: OffsetDateTime?, badBlockRange: Pair<Long, Long>, txHash: (Int) -> TxInfo?): List<TxEvent> =
    txsResults?.flatMapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
        val txHashData = txHash(index)

        // 1.11 bug - fees were not properly swept [0] - lower, [1] - higher
        val fee = if(height >= badBlockRange.first && height <= badBlockRange.second ) {
            tx.toBugFee(txHashData?.fee ?: Fee())
        } else {
            txHashData?.fee
        }

        if(tx.code?.toInt() != 0) {
           mutableListOf(toTxEvent(height, blockDateTime, txHashData?.txHash, fee ?: Fee()))
        } else {
            tx.events?.map {
                it.toTxEvent(height, blockDateTime, txHashData?.txHash, fee = fee ?: Fee())
            } ?: emptyList()
        }
    } ?: emptyList()

fun BlockResultsResponseResultTxsResults.toBugFee(fee: Fee): Fee {
    var msgBaseFeeList = listOf<MsgFeeAttribute>()
    this.events?.map { event ->
        if(event.type?.repeatDecodeBase64() == "provenance.msgfees.v1.EventMsgFees") {
            event.attributes?.toDecodedMap()?.map { attribute ->
                msgBaseFeeList = Gson().fromJson(attribute.value?.decodeBase64(), Array<MsgFeeAttribute>::class.java).toList()
            }
        }
    }

    val msgBaseFee = if(msgBaseFeeList.isNotEmpty()) msgBaseFeeList[0].total.toAmountDenom().amount.toLong() else 0L

    // if no msg fee take minimum
    val minimumFee = BigDecimal(this.gasWanted!!.toLong() * 1905).toLong()
    return if(msgBaseFee == 0L) Fee(fee = minimumFee, "nhash", fee.signerInfo) else fee
}

fun String.toAmountDenom(): AmountDenom {
    val amount = StringBuilder(this)
    val denom = StringBuilder()
    for (i in this.length - 1 downTo 0) {
        val ch = this[i]
        if (!ch.isDigit()) {
            amount.deleteCharAt(i)
            denom.insert(0, ch)
        } else {
            break
        }
    }
    return AmountDenom(amount.toString(), denom.toString())
}

fun String.toSignerAddr(hrp: String): List<String> {
    val tx = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(this)) ?: return mutableListOf()
    return tx.authInfo.signerInfosList.map {
        Keys.PubKey.parseFrom(it.publicKey.value).key.toByteArray().sha256hash160().toBech32(hrp).address.value
    }
}

fun BlockResultsResponseResult.txErroredEvents(blockDateTime: OffsetDateTime?, gasPriceUpdateBlockHeight: Long, badBlockRange: Pair<Long, Long>, txHash: (Int) -> TxInfo?): List<TxError> =
    txsResults?.filter {
        (it.code?.toInt() ?: 0) != 0
        }?.mapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->

        // 1.11 bug doesnt apply for errors
        txHash(index)?.fee
        tx.toBlockError(height, blockDateTime, txHash(index)?.txHash, fee = fee ?: Fee())
    } ?: emptyList()

fun BlockResultsResponseResultTxsResults.toBlockError(blockHeight: Long, blockDateTime: OffsetDateTime?, txHash: String?, fee: Fee): TxError =
    TxError(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        code = this.code?.toLong() ?: 0L,
        info = this.log ?: "",
        txHash = txHash ?: "",
        fee = fee,
    )

fun toTxEvent(blockHeight: Long, blockDateTime: OffsetDateTime?, txHash: String?, fee: Fee): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash ?: "",
        eventType = "ERROR",
        attributes = emptyList(),
        fee = fee
    )

fun BlockResultsResponseResultEvents.toTxEvent(
    blockHeight: Long,
    blockDateTime: OffsetDateTime?,
    txHash: String?,
    eventType: String? = "",
    fee: Fee
): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash ?: "",
        eventType = this.type ?: eventType,
        attributes = this.attributes?.map { event -> Event(event.key,event.value, event.index) } ?: emptyList(),
        fee = fee
    )

fun BlockResultsResponse.txEvents(blockDate: OffsetDateTime, badBlockRange: Pair<Long, Long>, txHash: (index: Int) -> TxInfo): List<TxEvent> =
    this.result.txEvents(blockDate, badBlockRange, txHash)

fun BlockResultsResponseResult.blockEvents(blockDateTime: OffsetDateTime?): List<BlockEvent> =
    beginBlockEvents?.map { e: BlockResultsResponseResultEvents ->
        BlockEvent(
            blockHeight = height,
            blockDateTime = blockDateTime,
            eventType = e.type ?: "",
            attributes = e.attributes?.map { event -> Event(event.key,event.value, event.index) } ?: emptyList()
        )
    } ?: emptyList()

fun BlockData.toStreamBlock(hrp: String, badBlockRange: Pair<Long, Long>): StreamBlockImpl {
    val blockDatetime = block.header?.dateTime()
    val blockEvents = blockResult.blockEvents(blockDatetime)
    val blockTxResults = blockResult.txsResults
    val txEvents = blockResult.txEvents(blockDatetime, badBlockRange) { index: Int -> block.txData(index, hrp) }
    return StreamBlockImpl(block, blockEvents, blockTxResults, txEvents)
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
