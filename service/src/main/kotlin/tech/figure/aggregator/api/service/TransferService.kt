package tech.figure.aggregator.api.service

import kotlinx.coroutines.async
import tech.figure.aggregate.proto.TransferServiceGrpcKt.TransferServiceCoroutineImplBase
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamRequest
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamResponse
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.COIN_TRANSFER
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.MARKER_SUPPLY
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.MARKER_TRANSFER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import tech.figure.aggregate.common.channel.ChannelImpl
import tech.figure.aggregate.common.db.DBClient
import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregate.common.db.model.TxMarkerSupply
import tech.figure.aggregate.common.db.model.TxMarkerTransfer
import tech.figure.aggregate.common.db.model.impl.TxResponseData
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.common.models.stream.CoinTransfer
import tech.figure.aggregate.common.models.stream.MarkerSupply
import tech.figure.aggregate.common.models.stream.MarkerTransfer
import tech.figure.aggregate.common.models.stream.impl.StreamTypeImpl
import tech.figure.aggregate.common.toOffsetDateTime
import tech.figure.aggregate.proto.CoinTransferOuterClass
import tech.figure.aggregate.proto.MarkerSupplyOuterClass
import tech.figure.aggregate.proto.MarkerTransferOuterClass
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import tech.figure.aggregator.api.service.extension.toCoinTransferResult
import tech.figure.aggregator.api.service.extension.toMarkerSupplyResult
import tech.figure.aggregator.api.service.extension.toMarkerTransferResult
import tech.figure.aggregator.api.service.extension.toProtoTimestamp

class TransferService(
    private val dbClient: DBClient,
    private val channel: ChannelImpl<StreamTypeImpl>,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : TransferServiceCoroutineImplBase(coroutineContext) {

    private val log = logger()
    override fun transferDataStream(request: StreamRequest): Flow<StreamResponse> {

        return runBlocking {
            val coinTransferResult = async { channel.asFlow(COIN_TRANSFER) }.await()
            val markerSupplyResult = async { channel.asFlow(MARKER_SUPPLY) }.await()
            val markerTransferResult = async { channel.asFlow(MARKER_TRANSFER) }.await()

            val historicalResponse = transferDataStreamHistorical(request)

            val liveCoinTransferFlow = coinTransferResult.toCoinTransferStreamResponse()
            val liveMarkerSupplyFlow = markerSupplyResult.toMarkerSupplyStreamResponse()
            val liveMarkerTransferFlow = markerTransferResult.toMarkerTransferStreamResponse()

            return@runBlocking merge(
                historicalResponse.toTxTypeResultFlow(request.streamType),
                liveCoinTransferFlow,
                liveMarkerSupplyFlow,
                liveMarkerTransferFlow
            )
        }
    }

    private fun transferDataStreamHistorical(request: StreamRequest): List<TxResponseData> =
        dbClient.streamTransferHistorical(request)
}

fun List<TxResponseData>.toTxTypeResultFlow(streamType: StreamType) =
    this.map {
        when (streamType) {
            COIN_TRANSFER -> (it as TxCoinTransferData).toCoinTransferResult()
            MARKER_TRANSFER -> (it as TxMarkerTransfer).toMarkerTransferResult()
            MARKER_SUPPLY -> (it as TxMarkerSupply).toMarkerSupplyResult()
            else -> error("Failed to provide stream type in the request.")
        }
    }.asFlow()

fun Flow<StreamTypeImpl>.toCoinTransferStreamResponse(): Flow<StreamResponse> =
    this.map { it as CoinTransfer
        val data = CoinTransferOuterClass.CoinTransfer.newBuilder()
            .setEventType(it.eventType)
            .setBlockHeight(it.blockHeight)
            .setBlockTimestamp(it.blockTimestamp.toString().toOffsetDateTime().toProtoTimestamp())
            .setTxHash(it.txHash)
            .setRecipient(it.recipient)
            .setSender(it.sender)
            .setAmount(it.amount)
            .setDenom(it.denom)
            .build()

        StreamResponse.newBuilder()
            .setCoinTransfer(data)
            .build()
    }

fun Flow<StreamTypeImpl>.toMarkerSupplyStreamResponse(): Flow<StreamResponse> =
    this.map {it as MarkerSupply
        val data = MarkerSupplyOuterClass.MarkerSupply.newBuilder()
            .setEventType(it.eventType)
            .setBlockHeight(it.blockHeight)
            .setBlockTimestamp(it.blockTimestamp.toString().toOffsetDateTime().toProtoTimestamp())
            .setCoins(it.coins)
            .setDenom(it.denom)
            .setAdministrator(it.administrator)
            .setToAddress(it.toAddress)
            .setFromAddress(it.fromAddress)
            .setMetadataBase(it.metadataBase)
            .setMetadataDescription(it.metadataDescription)
            .setMetadataDisplay(it.metadataDisplay)
            .setMetadataDenomUnits(it.metadataDenomUnits)
            .setMetadataName(it.metadataName)
            .setMetadataSymbol(it.metadataSymbol)
            .build()

        StreamResponse.newBuilder()
            .setMarkerSupply(data)
            .build()
    }

fun Flow<StreamTypeImpl>.toMarkerTransferStreamResponse(): Flow<StreamResponse> =
    this.map {it as MarkerTransfer
        val data = MarkerTransferOuterClass.MarkerTransfer.newBuilder()
            .setEventType(it.eventType)
            .setBlockHeight(it.blockHeight)
            .setBlockTimestamp(it.blockTimestamp.toOffsetDateTime().toProtoTimestamp())
            .setAmount(it.amount)
            .setDenom(it.denom)
            .setAdministrator(it.administrator)
            .setToAddress(it.toAddress)
            .setFromAddress(it.fromAddress)
            .build()

        StreamResponse.newBuilder()
            .setMarkerTransfer(data)
            .build()
    }

