package io.provenance.aggregate.service.aws.dynamodb

import io.provenance.aggregate.service.aws.dynamodb.extensions.toBlockStorageMetadata
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.batch.BatchId
import io.provenance.aggregate.service.stream.models.StreamBlock
import io.provenance.aggregate.service.utils.DelayShim
import io.provenance.aggregate.service.utils.backoff
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.ExperimentalTime

// See https://aws.amazon.com/blogs/developer/introducing-enhanced-dynamodb-client-in-the-aws-sdk-for-java-v2 for usage

open class AwsDynamo(
    private val dynamoClient: DynamoDbAsyncClient,
    private val blockBatchTable: DynamoTable,
    private val blockMetadataTable: DynamoTable,
    private val serviceMetadataTable: DynamoTable
) : AwsDynamoInterface, DelayShim {

    companion object {
        const val DYNAMODB_MAX_TRANSACTION_RETRIES: Int = 5
        const val DYNAMODB_MAX_TRANSACTION_ITEMS: Int = 25
    }

    private val log = logger()

    private val enhancedClient: DynamoDbEnhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(dynamoClient).build()

    private val serviceMetadataTableSchema: ImmutableTableSchema<ServiceMetadata> =
        TableSchema.fromImmutableClass(ServiceMetadata::class.java)

    private val blockBatchTableSchema: ImmutableTableSchema<BlockBatch> =
        TableSchema.fromImmutableClass(BlockBatch::class.java)

    private val blockMetadataTableSchema: ImmutableTableSchema<BlockStorageMetadata> =
        TableSchema.fromImmutableClass(BlockStorageMetadata::class.java)

    internal val SERVICE_METADATA_TABLE: DynamoDbAsyncTable<ServiceMetadata> =
        enhancedClient.table(serviceMetadataTable.name, serviceMetadataTableSchema)

    internal val BLOCK_BATCH_TABLE: DynamoDbAsyncTable<BlockBatch> =
        enhancedClient.table(blockBatchTable.name, blockBatchTableSchema)

    internal val BLOCK_METADATA_TABLE: DynamoDbAsyncTable<BlockStorageMetadata> =
        enhancedClient.table(blockMetadataTable.name, blockMetadataTableSchema)

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? {
        return BLOCK_METADATA_TABLE.getItem(Key.builder().partitionValue(blockHeight).build()).await()
    }

    @OptIn(FlowPreview::class)
    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> {
        val reader = ReadBatch.builder(BlockStorageMetadata::class.java)
            .mappedTableResource(BLOCK_METADATA_TABLE)

        for (blockHeight in blockHeights) {
            reader.addGetItem(Key.builder().partitionValue(blockHeight).build())
        }

        return enhancedClient.batchGetItem { request: BatchGetItemEnhancedRequest.Builder ->
            request.addReadBatch(reader.build()).build()
        }
            .asFlow()
            .flatMapConcat { page: BatchGetResultPage -> page.resultsForTable(BLOCK_METADATA_TABLE).asFlow() }
    }

    private fun createStreamBlockPutRequests(
        batchId: BatchId,
        blocks: List<StreamBlock>
    ): List<TransactPutItemEnhancedRequest<BlockStorageMetadata>> =
        blocks.map { block ->
            TransactPutItemEnhancedRequest.builder(BlockStorageMetadata::class.java)
                .item(block.toBlockStorageMetadata(batchId))
                .build()
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult {

        // TODO: figure out how to fetch + update this transactionally:
        val storedMaxHistoricalHeight: Long? = getMaxHistoricalBlockHeight()

        // Find the historical max block height in the bunch:
        val foundMaxHistoricalHeight: Long? =
            blocks.filter { it.historical }
                .mapNotNull { it.block.header }
                .map { it.height }
                .maxOrNull()

        var lastException: Throwable? = null

        // See https://stackoverflow.com/q/54245599. This happens due to AWS use of Optimistic Concurrency Control
        // Using jitter for retry timing: https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
        // Try up to 5 times:
        for (attempt in 0..DYNAMODB_MAX_TRANSACTION_RETRIES) {
            try {
                val futures = mutableListOf<Deferred<Void>>()
                val totalProcessed = AtomicInteger(0)
                var reservedSlots: Int = 0

                futures.add(
                    enhancedClient.transactWriteItems { request: TransactWriteItemsEnhancedRequest.Builder ->
                        // Add the `BlockBatch` entry:
                        request.addPutItem(
                            BLOCK_BATCH_TABLE,
                            TransactPutItemEnhancedRequest
                                .builder(BlockBatch::class.java)
                                .item(batch)
                                .build()
                        )
                        reservedSlots += 1
                        totalProcessed.incrementAndGet()

                        // Put/Update the maximum historical block height seen:
                        if (foundMaxHistoricalHeight != null) {
                            log.info("Found historical block height -> $foundMaxHistoricalHeight; stored = $storedMaxHistoricalHeight")
                            val prop =
                                ServiceMetadata.Properties.MaxHistoricalBlockHeight.newEntry(foundMaxHistoricalHeight.toString())
                            if (storedMaxHistoricalHeight == null || foundMaxHistoricalHeight > storedMaxHistoricalHeight) {
                                request.addPutItem(
                                    SERVICE_METADATA_TABLE,
                                    TransactPutItemEnhancedRequest.builder(ServiceMetadata::class.java)
                                        .item(prop)
                                        .build()
                                )
                                reservedSlots += 1
                                totalProcessed.incrementAndGet()
                            }
                        }
                        // For the initial put batch for blocks, we need to subtract `reservedSlots` from
                        // `DYNAMODB_MAX_TRANSACTION_ITEMS`, to stay under the limit:
                        createStreamBlockPutRequests(
                            BatchId(batch.batchId),
                            blocks.take(DYNAMODB_MAX_TRANSACTION_ITEMS - reservedSlots)
                        ).forEach {
                            request.addPutItem(BLOCK_METADATA_TABLE, it)
                            totalProcessed.incrementAndGet()
                        }
                    }
                        .asDeferred()
                )

                futures.addAll(
                    blocks.chunked(DYNAMODB_MAX_TRANSACTION_ITEMS)
                        .map { chunk ->
                            val future: CompletableFuture<Void> =
                                enhancedClient.transactWriteItems { request: TransactWriteItemsEnhancedRequest.Builder ->
                                    createStreamBlockPutRequests(BatchId(batch.batchId), chunk).forEach {
                                        request.addPutItem(BLOCK_METADATA_TABLE, it)
                                        totalProcessed.incrementAndGet()
                                    }
                                }
                            future.asDeferred()
                        }
                )

                futures.awaitAll()

                return WriteResult.ok(totalProcessed.getAcquire())

            } catch (txCancelledEx: TransactionCanceledException) {

                lastException = txCancelledEx

                // Wait before retrying again:
                // TODO: Replace with delay. See `DelayShim` interface notes
                doDelay(backoff(attempt, base = 100.0))
            }
        }

        throw lastException ?: Exception("tx failed: impossible condition")
    }

    /**
     * Returns the maximum historical block height seen, if any.
     */
    override suspend fun getMaxHistoricalBlockHeight(): Long? =
        runCatching {
            SERVICE_METADATA_TABLE.getItem(
                Key.builder()
                    .partitionValue(ServiceMetadata.Properties.MaxHistoricalBlockHeight.key)
                    .build()
            )
                .await()
        }
            .getOrNull()
            ?.value
            ?.toLongOrNull()

    /**
     * Unconditionally overwrite the entry where the partition key "Property" is equal to the name value of
     * `ServiceMetadata.Properties.MAX_HISTORICAL_BLOCK_HEIGHT` * with attribute "Value" set to the string-ified
     * version of `blockHeight`.
     */
    override suspend fun writeMaxHistoricalBlockHeight(blockHeight: Long): WriteResult {
        SERVICE_METADATA_TABLE.putItem { request ->
            request.item(ServiceMetadata.Properties.MaxHistoricalBlockHeight.newEntry(blockHeight.toString()))
        }
            .await()

        return WriteResult.ok()
    }
}