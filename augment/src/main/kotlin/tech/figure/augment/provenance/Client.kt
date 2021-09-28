package tech.figure.augment.provenance

import cosmos.base.tendermint.v1beta1.Query.GetLatestBlockRequest
import cosmos.base.tendermint.v1beta1.Query.GetLatestBlockResponse
import cosmos.base.tendermint.v1beta1.ServiceGrpc as NodeGrpc
import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.bank.v1beta1.QueryGrpc as BankGrpc
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.stub.AbstractFutureStub
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

object ProvenanceConst {
    val BLOCK_HEIGHT = "x-cosmos-block-height"
}

class ProvenanceClient(
    channel: ManagedChannel,
    private val semaphore: Semaphore,
) {
    private val bankService = BankGrpc.newFutureStub(channel)
    private val nodeService = NodeGrpc.newFutureStub(channel)

    suspend fun getLatestBlock(): GetLatestBlockResponse =
        semaphore.withPermit {
            nodeService
                .withDeadlineAfter(10, TimeUnit.SECONDS)
                .getLatestBlock(
                    GetLatestBlockRequest.getDefaultInstance()
                ).asDeferred().await()
        }

    suspend fun denomBalance(
        address: String,
        denom: String,
        blockHeight: String,
    ): BankOuterClass.QueryBalanceResponse =
        semaphore.withPermit {
            bankService
                .withDeadlineAfter(10, TimeUnit.SECONDS)
                .addBlockHeight(blockHeight)
                .balance(BankOuterClass.QueryBalanceRequest.newBuilder()
                    .setAddress(address)
                    .setDenom(denom)
                    .build()
                ).asDeferred().await()
        }
}

fun<S : AbstractStub<S>> S.addBlockHeight(blockHeight: String): S = this.also {
    val metadata = Metadata().also {
        it.put(
            Metadata.Key.of(ProvenanceConst.BLOCK_HEIGHT, Metadata.ASCII_STRING_MARSHALLER),
            blockHeight,
        )
    }

    MetadataUtils.attachHeaders(this, metadata)
}
