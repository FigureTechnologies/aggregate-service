package tech.figure.aggregator.api.service

import TransferServiceGrpcKt.TransferServiceCoroutineImplBase
import TransferServiceOuterClass.CoinTransferRequest
import TransferServiceOuterClass.CoinTransferResponse
import TransferServiceOuterClass.MarkerSupplyRequest
import TransferServiceOuterClass.MarkerSupplyResponse
import TransferServiceOuterClass.MarkerTransferRequest
import TransferServiceOuterClass.MarkerTransferResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import tech.figure.aggregate.common.db.DBClient
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import tech.figure.aggregator.api.service.extension.toCoinTransferResult
import tech.figure.aggregator.api.service.extension.toMarkerSupplyResult
import tech.figure.aggregator.api.service.extension.toMarkerTransferResult

class TransferService(
    private val dbClient: DBClient,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : TransferServiceCoroutineImplBase(coroutineContext) {

    override fun coinTransferStream(request: CoinTransferRequest): Flow<CoinTransferResponse> =
        dbClient.streamCoinTransfer(request.blockHeight, request.denomList).map {
            it.toCoinTransferResult()
        }.asFlow()

    override fun markerSupplyStream(request: MarkerSupplyRequest): Flow<MarkerSupplyResponse> =
        dbClient.streamMarkerSupply(request.blockHeight, request.denomList).map {
            it.toMarkerSupplyResult()
        }.asFlow()

    override fun markerTransferStream(request: MarkerTransferRequest): Flow<MarkerTransferResponse> =
        dbClient.streamMarkerTransfer(request.blockHeight, request.denomList).map {
            it.toMarkerTransferResult()
        }.asFlow()

}


