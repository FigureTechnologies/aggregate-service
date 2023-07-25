package tech.figure.aggregator.api.service.extension

import tech.figure.aggregate.proto.coinTransfer
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import tech.figure.aggregate.proto.markerSupply
import tech.figure.aggregate.proto.markerTransfer
import tech.figure.aggregate.proto.streamResponse
import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregate.common.db.model.TxMarkerSupply
import tech.figure.aggregate.common.db.model.TxMarkerTransfer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun TxMarkerSupply.toMarkerSupplyResult() = streamResponse {
    markerSupply = this@toMarkerSupplyResult.toProto()
}

fun TxMarkerTransfer.toMarkerTransferResult() = streamResponse {
    markerTransfer = this@toMarkerTransferResult.toProto()
}

fun TxCoinTransferData.toCoinTransferResult() = streamResponse {
     coinTransfer = this@toCoinTransferResult.toProto()
}

fun TxMarkerTransfer.toProto() = markerTransfer {
    val data = this@toProto
    eventType = data.eventType
    blockHeight = data.blockHeight
    blockTimestamp = data.blockTimestamp.toInstant().atOffset(ZoneOffset.UTC).toProtoTimestamp()
    txHash = data.txHash
    amount = data.amount
    denom = data.denom
    administrator = data.administrator
    toAddress = data.toAddress
    fromAddress = data.fromAddress
}

fun TxMarkerSupply.toProto() = markerSupply {
    val data = this@toProto
    eventType = data.eventType.toString()
    blockHeight = data.blockHeight
    blockTimestamp = data.blockTimestamp.toInstant().atOffset(ZoneOffset.UTC).toProtoTimestamp()
    txHash = data.txHash.toString()
    coins = data.coins.toString()
    denom = data.denom.toString()
    amount = data.denom.toString()
    administrator = data.denom.toString()
    toAddress = data.amount.toString()
    fromAddress = data.amount.toString()
    metadataBase = data.metadataBase.toString()
    metadataDescription = data.metadataDescription.toString()
    metadataDisplay = data.metadataDisplay.toString()
    metadataDenomUnits = data.metadataDisplay.toString()
    metadataName = data.metadataName.toString()
    metadataSymbol = data.metadataSymbol.toString()
}

fun TxCoinTransferData.toProto() = coinTransfer {
    val data = this@toProto
    blockHeight = data.blockHeight
    blockTimestamp = data.blockTimestamp.toInstant().atOffset(ZoneOffset.UTC).toProtoTimestamp()
    eventType = data.eventType
    txHash = data.txHash
    recipient = data.recipient
    sender = data.sender
    amount = data.amount
    denom = data.denom
}

fun OffsetDateTime.toProtoTimestamp(): Timestamp = Timestamp.newBuilder()
    .setValue(this)
    .build()

fun Timestamp.Builder.setValue(odt: OffsetDateTime): Timestamp.Builder = setValue(odt.toInstant())

fun Timestamp.Builder.setValue(instant: Instant): Timestamp.Builder {
    val max = Timestamps.MAX_VALUE
    val min = Timestamps.MIN_VALUE
    when {
        instant.epochSecond > max.seconds -> this.seconds = max.seconds
        instant.epochSecond < min.seconds -> this.seconds = min.seconds
        else -> this.seconds = instant.epochSecond
    }

    when {
        instant.nano > max.nanos -> this.nanos = max.nanos
        instant.nano < min.nanos -> this.nanos = min.nanos
        else -> this.nanos = instant.nano
    }

    return this
}
