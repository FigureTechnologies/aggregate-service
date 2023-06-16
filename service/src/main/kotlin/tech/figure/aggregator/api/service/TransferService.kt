package tech.figure.aggregator.api.service

import TransferServiceGrpcKt.TransferServiceCoroutineImplBase
import TransferServiceOuterClass.StreamRequest
import TransferServiceOuterClass.StreamResponse
import TransferServiceOuterClass.StreamType.COIN_TRANSFER
import TransferServiceOuterClass.StreamType.MARKER_SUPPLY
import TransferServiceOuterClass.StreamType.MARKER_TRANSFER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import tech.figure.aggregate.common.db.DBClient
import tech.figure.aggregate.common.db.model.TxCoinTransferData
import tech.figure.aggregate.common.db.model.TxMarkerSupply
import tech.figure.aggregate.common.db.model.TxMarkerTransfer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import tech.figure.aggregator.api.service.extension.toCoinTransferResult
import tech.figure.aggregator.api.service.extension.toMarkerSupplyResult
import tech.figure.aggregator.api.service.extension.toMarkerTransferResult

class TransferService(
    private val dbClient: DBClient,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : TransferServiceCoroutineImplBase(coroutineContext) {

    override fun transferDataStream(request: StreamRequest): Flow<StreamResponse> =
        dbClient.streamTransfer(request).map {
            when(request.streamType) {
                COIN_TRANSFER -> (it as TxCoinTransferData).toCoinTransferResult()
                MARKER_TRANSFER -> (it as TxMarkerTransfer).toMarkerTransferResult()
                MARKER_SUPPLY -> (it as TxMarkerSupply).toMarkerSupplyResult()
                else -> error("Failed to provide stream type in the request.")
            }
        }.asFlow()
}


