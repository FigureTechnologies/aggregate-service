package io.provenance.aggregate.service.clients

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.paginators.BatchGetItemPublisher
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class FailingDynamoDbAsyncClient(
    val client: DynamoDbAsyncClient,
    val predicate: (method: String, callCount: Int) -> Boolean = { _, _ -> true }
) :
    DynamoDbAsyncClient by client {

    private var callCount = mutableMapOf(
        "transactWriteItems_0" to 0,
        "transactWriteItems_1" to 0
    )

    fun reset() {
        for (k in callCount.keys) {
            callCount[k] = 0
        }
    }

    private fun checkIfRaiseException(method: String) {
        callCount.computeIfPresent(method) { _: String, count: Int -> count + 1 }
        if (predicate(method, callCount[method] ?: 0)) {
            val reasons = listOf(CancellationReason.builder().message("Transaction conflict").build())
            val e: TransactionCanceledException = TransactionCanceledException
                .builder()
                .cancellationReasons(reasons)
                .build()
            throw e
        }
    }

    override fun batchGetItemPaginator(batchGetItemRequest: BatchGetItemRequest?): BatchGetItemPublisher? {
        return client.batchGetItemPaginator(batchGetItemRequest)
    }

    override fun transactWriteItems(transactWriteItemsRequest: TransactWriteItemsRequest?): CompletableFuture<TransactWriteItemsResponse?>? {
        checkIfRaiseException("transactWriteItems_0")
        return client.transactWriteItems(transactWriteItemsRequest)
    }

    override fun transactWriteItems(
        transactWriteItemsRequest: Consumer<TransactWriteItemsRequest.Builder?>?
    ): CompletableFuture<TransactWriteItemsResponse?>? {
        checkIfRaiseException("transactWriteItems_1")
        return client.transactWriteItems(transactWriteItemsRequest)
    }
}