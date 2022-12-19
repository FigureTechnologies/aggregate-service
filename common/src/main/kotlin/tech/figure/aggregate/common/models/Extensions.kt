package tech.figure.aggregate.common.models

import com.google.common.io.BaseEncoding
import com.google.gson.Gson
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.protobuf.extensions.time.toOffsetDateTime
import tech.figure.aggregate.common.models.block.BlockTxData
import tech.figure.aggregate.common.models.tx.TxEvent
import io.provenance.hdwallet.bech32.toBech32
import io.provenance.hdwallet.common.hashing.sha256hash160
import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.common.models.fee.Fee
import tech.figure.aggregate.common.models.fee.SignerInfo
import tech.figure.block.api.proto.BlockOuterClass
import tech.figure.block.api.proto.BlockServiceOuterClass
import tech.figure.block.api.proto.BlockServiceOuterClass.BlockStreamResult
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.OffsetDateTime

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

fun BlockOuterClass.Fee.toBugFee(events: List<TxEvent>, gasWanted: Long, code: Long, msgBaseFeeHeight: Long): Long {
    var msgBaseFeeList = listOf<MsgFeeAttribute>()

    events.map { event ->
        if(event.eventType == "provenance.msgfees.v1.EventMsgFees") {
            event.attributes.map { attribute ->
                msgBaseFeeList = Gson().fromJson(attribute.value, Array<MsgFeeAttribute>::class.java).toList()
            }
        }
    }
    val msgBaseFee = if(msgBaseFeeList.isNotEmpty()) msgBaseFeeList[0].total.toAmountDenom().amount.toLong() else 0L

    //if no msg fee take minimum
    val minimumFee = BigDecimal(gasWanted * 1905).toLong()

    return if(msgBaseFee == 0L) minimumFee else this.fee
}

fun BlockOuterClass.SignerInfo.toSignerInfo(): SignerInfo =
    SignerInfo(
        signerAddrs  = this.signerAddrList,
        incurrAddr = this.incurringAddr
    )

fun BlockOuterClass.Fee.toFee(events: List<TxEvent>, code: Long, gasWanted: Long, badBlockRange: Pair<Long, Long>, msgBaseFeeHeight: Long): Fee {
    val fee = if(code.toInt() != 0) {
        if(msgBaseFeeHeight <= height) {
            BigDecimal(gasWanted * 1905).toLong()
        } else {
            this.fee
        }
    } else {
        if(this.height >= badBlockRange.first && this.height <= badBlockRange.second) {
            this.toBugFee(events, gasWanted, code, msgBaseFeeHeight)
        } else {
            this.fee
        }
    }

    return Fee(
        fee = fee,
        denom = this.denom,
        signerInfo = this.signerInfo.toSignerInfo()
    )
}

fun List<BlockOuterClass.TxEvent>.txEvents(
    blockDateTime: OffsetDateTime?,
    height: Long,
    txCode: Long
): List<TxEvent> {
    return this.map { event ->
        TxEvent(
            blockHeight = height,
            blockDateTime = blockDateTime,
            txHash = event.txHash,
            eventType = if(txCode.toInt() != 0) "ERROR" else event.eventType,
            attributes = event.attributesList.map { attr -> Event(attr.key, attr.value, attr.index) }
        )
    }
}

fun List<BlockOuterClass.Transaction>.blockEvents(blockDateTime: OffsetDateTime?, badBlockRange: Pair<Long, Long>, msgBaseFeeHeight: Long): List<BlockTxData> {
    return this.map { tx ->
        val events = tx.eventsList.txEvents(blockDateTime, tx.blockHeight, tx.code)
        BlockTxData(
            txHash = tx.txHash,
            blockHeight = tx.blockHeight,
            blockDateTime = blockDateTime,
            code = tx.code,
            gasUsed = tx.gasUsed,
            gasWanted = tx.gasWanted,
            events = events,
            fee = tx.fee.toFee(events, tx.code, tx.gasWanted, badBlockRange, msgBaseFeeHeight)
        )
    }
}

fun BlockStreamResult.toStreamBlock(hrp: String, badBlockRange: Pair<Long, Long>, msgBaseFeeHeight: Long): StreamBlock {
    val blockDateTime = this.blockResult.block.time.toOffsetDateTime()
    val blockTxData = this.blockResult.block.transactionsList.blockEvents(blockDateTime, badBlockRange, msgBaseFeeHeight)
    val block = this.blockResult.block
    return StreamBlock(block, blockTxData, blockDateTime = blockDateTime)
}
