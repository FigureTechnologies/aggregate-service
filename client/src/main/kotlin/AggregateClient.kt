package tech.figure.aggregate.client

import tech.figure.aggregate.proto.TransferServiceOuterClass.AllDenomRequest
import tech.figure.aggregate.proto.TransferServiceOuterClass.FilteredDenomRequest
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamResponse
import tech.figure.aggregate.proto.TransferServiceOuterClass.StreamType
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.flow.Flow
import tech.figure.aggregate.proto.TransferServiceGrpcKt
import tech.figure.aggregate.proto.streamRequest
import java.io.Closeable
import java.util.concurrent.TimeUnit.SECONDS

class AggregateClient(
    host: String,
    port: Int
): Closeable {

    private val config = grpcConfig(host, port)
    private val channel = config.channel.usePlaintext().build()
    private val transferService = TransferServiceGrpcKt.TransferServiceCoroutineStub(channel)

    fun streamTransferData(
        blockHeight: Long,
        denom: List<String>,
        type: StreamType
    ) : Flow<StreamResponse> {
        return transferService.transferDataStream(
            streamRequest {
                if (denom.isEmpty()) {
                    this.allDenomRequest = AllDenomRequest.newBuilder()
                        .setBlockHeight(blockHeight)
                        .build()
                } else {
                    this.filteredDenomRequest =
                        FilteredDenomRequest.newBuilder()
                            .setBlockHeight(blockHeight)
                            .addAllDenom(denom)
                            .build()
                }
                this.streamType = type
            }
        )
    }

    override fun close() {
        channel.shutdown().awaitTermination(10, SECONDS)
    }
}

data class GRPCConfig(val channel: ManagedChannelBuilder<*>)

fun grpcConfig(host: String, port: Int): GRPCConfig {
    return GRPCConfig(ManagedChannelBuilder.forAddress(host, port))
}

