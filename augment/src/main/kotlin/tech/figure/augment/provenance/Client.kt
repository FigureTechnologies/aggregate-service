package tech.figure.augment.provenance

import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.bank.v1beta1.QueryGrpc as BankGrpc
import io.grpc.ManagedChannel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.guava.asDeferred
import java.util.concurrent.TimeUnit

class ProvenanceClient(
    private val chainId: String,
    private val channel: ManagedChannel,
) {
    private val bankService = BankGrpc.newFutureStub(channel)

    fun denomBalance(address: String, denom: String): Deferred<BankOuterClass.QueryBalanceResponse> =
        bankService
            .withDeadlineAfter(10, TimeUnit.SECONDS)
            .balance(BankOuterClass.QueryBalanceRequest.newBuilder()
                .setAddress(address)
                .setDenom(denom)
                .build()
            ).asDeferred()
}
