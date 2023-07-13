package tech.figure.aggregate.common.channel

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import tech.figure.aggregate.common.models.stream.CoinTransfer
import tech.figure.aggregate.common.models.stream.MarkerSupply
import tech.figure.aggregate.common.models.stream.MarkerTransfer
import tech.figure.aggregate.common.models.stream.impl.StreamTypeImpl
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.COIN_TRANSFER
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.MARKER_TRANSFER
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType.MARKER_SUPPLY

class ChannelImpl<out T : StreamTypeImpl> {

    private val coinTransferChannel = Channel<CoinTransfer>()
    private val markerSupplyChannel = Channel<MarkerSupply>()
    private val markerTransferChannel = Channel<MarkerTransfer>()

    fun send(t: StreamTypeImpl) {
        when(t) {
            is CoinTransfer -> coinTransferChannel.trySend(t)
            is MarkerTransfer -> markerTransferChannel.trySend(t)
            is MarkerSupply -> markerSupplyChannel.trySend(t)
        }
    }

    fun asFlow(type: StreamType): Flow<StreamTypeImpl> {
       return when(type) {
           COIN_TRANSFER -> coinTransferChannel.receiveAsFlow()
           MARKER_TRANSFER -> markerTransferChannel.receiveAsFlow()
           MARKER_SUPPLY -> markerSupplyChannel.receiveAsFlow()
           else -> error("Stream type not specified")
       }
    }
}
