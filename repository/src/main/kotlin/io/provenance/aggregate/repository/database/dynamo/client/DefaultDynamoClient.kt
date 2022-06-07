package io.provenance.aggregate.repository.database.dynamo.client

import io.provenance.aggregate.common.DynamoTable
import io.provenance.aggregate.repository.database.dynamo.toBlockStorageMetadata
import io.provenance.aggregate.common.logger
import io.provenance.aggregate.common.models.BatchId
import io.provenance.aggregate.common.models.StreamBlock
import io.provenance.aggregate.common.utils.DelayShim
import io.provenance.aggregate.common.utils.backoff
import io.provenance.aggregate.common.utils.timestamp
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.database.dynamo.BlockBatch
import io.provenance.aggregate.repository.database.dynamo.BlockStorageMetadata
import io.provenance.aggregate.repository.database.dynamo.WriteResult
import io.provenance.aggregate.repository.database.dynamo.client.DefaultDynamoClient.ServiceMetadata.Props
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import software.amazon.awssdk.enhanced.dynamodb.*
import software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.ExperimentalTime

/**
 * The default implementation of AWS Dynamo client
 *
 * @property dynamoClient An AWS SDK async client instance
 * @property blockBatchTable A [DynamoTable] representing the table which tracks block batching information
 * @property blockMetadataTable A [DynamoTable] representing the table which tracks metadata about blocks
 * @property serviceMetadataTable A [DynamoTable] representing the table which stores data about the service itself
 *
 * @see https://aws.amazon.com/blogs/developer/introducing-enhanced-dynamodb-client-in-the-aws-sdk-for-java-v2
 */
open class DefaultDynamoClient(
    private val dynamoClient: DynamoDbAsyncClient,
    private val blockBatchTable: DynamoTable,
    private val blockMetadataTable: DynamoTable,
    private val serviceMetadataTable: DynamoTable
) : IDynamoClient, DelayShim, RepositoryBase {

    companion object {
        const val DYNAMODB_MAX_TRANSACTION_RETRIES: Int = 5
        const val DYNAMODB_MAX_TRANSACTION_ITEMS: Int = 25
    }

    object ServiceMetadata {
        object Props {
            const val MAX_HISTORICAL_BLOCK_HEIGHT = "MaxHistoricalBlockHeight"
        }
    }

    private val log = logger()

    private val enhancedClient: DynamoDbEnhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(dynamoClient).build()

    private val blockBatchTableSchema: ImmutableTableSchema<BlockBatch> =
        TableSchema.fromImmutableClass(BlockBatch::class.java)

    private val blockMetadataTableSchema: ImmutableTableSchema<BlockStorageMetadata> =
        TableSchema.fromImmutableClass(BlockStorageMetadata::class.java)

    val BLOCK_BATCH_TABLE: DynamoDbAsyncTable<BlockBatch> =
        enhancedClient.table(blockBatchTable.name, blockBatchTableSchema)

    val BLOCK_METADATA_TABLE: DynamoDbAsyncTable<BlockStorageMetadata> =
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

    /**
     * Retry a wrapped transaction.
     *
     * See https://stackoverflow.com/q/54245599. This happens due to AWS use of Optimistic Concurrency Control
     * Using jitter for retry timing: https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun <T> retryTx(upTo: Int, context: String, f: suspend () -> T): T {
        var ex: DynamoDbException? = null
        for (attempt in 0..upTo) {
            try {
                return f()
            } catch (dynamoEx: DynamoDbException) {
                log.info("DynamoDbException: Retry attempt: $attempt")
                log.info("  context: $context")
                log.info("  message: ${dynamoEx.message}")
                if (dynamoEx is TransactionCanceledException) {
                    log.info("  TransactionCanceledException reasons")
                    for (reason in dynamoEx.cancellationReasons()) {
                        log.info("    - ${reason.code()}:${reason.message()} => ${reason.item()}")
                    }
                }
                ex = dynamoEx
                // TODO: Replace with delay. See `DelayShim` interface notes
                doDelay(backoff(attempt, base = 100.0))
            }
        }
        throw ex ?: error("tx cancelled: impossible state")
    }

    @OptIn(ExperimentalTime::class, kotlin.ExperimentalStdlibApi::class)
    override suspend fun trackBlocks(batch: BlockBatch, blocks: Iterable<StreamBlock>): WriteResult {

        // Find the historical max block height in the bunch:
        val foundMaxHistoricalHeight: Long? =
            blocks.filter { it.historical }
                .mapNotNull { it.block.header }
                .map { it.height }
                .maxOrNull()

        val totalProcessed = AtomicInteger(0)

        buildList {
            add(
                retryTx(DYNAMODB_MAX_TRANSACTION_RETRIES, "BLOCK_BATCH/SERVICE_METADATA") {
                    enhancedClient.transactWriteItems { request ->
                        // Add the `BlockBatch` entry:
                        request.addPutItem(
                            BLOCK_BATCH_TABLE,
                            TransactPutItemEnhancedRequest
                                .builder(BlockBatch::class.java)
                                .item(batch)
                                .build()
                        )
                        totalProcessed.incrementAndGet()
                    }
                        .asDeferred()
                }
            )
            addAll(
                blocks
                    .chunked(DYNAMODB_MAX_TRANSACTION_ITEMS)
                    .filter { it.isNotEmpty() }
                    .map { blockChunk: List<StreamBlock> ->
                        val context = "block-chunk:[${blockChunk.map { it.height }.joinToString("")}]"
                        retryTx(DYNAMODB_MAX_TRANSACTION_RETRIES, context) {
                            enhancedClient.transactWriteItems { request ->
                                createStreamBlockPutRequests(BatchId(batch.batchId), blockChunk).forEach {
                                    request.addPutItem(BLOCK_METADATA_TABLE, it)
                                    totalProcessed.incrementAndGet()
                                }
                            }
                                .asDeferred()
                        }
                    }
            )
        }
            .awaitAll()

        // Update the max block height as well:
        val updateHeightResult =
            foundMaxHistoricalHeight?.let { writeBlockCheckpoint(it) } ?: WriteResult.empty()

        return WriteResult.ok(totalProcessed.getAcquire()) + updateHeightResult
    }

    /**
     * Returns the maximum historical block height seen, if any.
     *
     * @return The maximum historical block height, if any.
     */
    override suspend fun getBlockCheckpoint(): Long? =
        runCatching {
            dynamoClient.getItem(
                GetItemRequest
                    .builder()
                    .tableName(serviceMetadataTable.name)
                    .key(
                        mapOf(
                            "Property" to AttributeValue.builder().s(Props.MAX_HISTORICAL_BLOCK_HEIGHT)
                                .build()
                        )
                    )
                    .build()
            )
                .await()
                .item()["Value"]
                ?.let { it.n() }
        }
            .onFailure { e -> log.error("$e") }
            .getOrNull()
            ?.toLongOrNull()

    private fun updateMaxHistoricalBlockHeight(
        blockHeight: Long,
        request: UpdateItemRequest.Builder
    ): UpdateItemRequest.Builder {
        val propertyAttr =
            AttributeValue.builder().s(Props.MAX_HISTORICAL_BLOCK_HEIGHT).build()
        val blockHeightAttr = AttributeValue.builder().n(blockHeight.toString()).build()
        val updatedAtAttr = AttributeValue.builder().s(timestamp()).build()
        return request
            .tableName(serviceMetadataTable.name)
            .key(mapOf("Property" to propertyAttr))
            .updateExpression("SET #Value = :update_value, #UpdatedAt = :updated_at")
            .conditionExpression("attribute_not_exists(#Property) OR (attribute_exists(#Property) AND #Property = :prop_name AND #Value < :cond_value)")
            .expressionAttributeNames(
                mapOf(
                    "#Property" to "Property",
                    "#Value" to "Value",
                    "#UpdatedAt" to "UpdatedAt"
                )
            )
            .expressionAttributeValues(
                mapOf(
                    ":prop_name" to propertyAttr,
                    ":update_value" to blockHeightAttr,
                    ":updated_at" to updatedAtAttr,
                    ":cond_value" to blockHeightAttr,
                )
            )
            .returnValues("UPDATED_NEW")
    }

    override suspend fun saveBlock(block: StreamBlock) {
        TODO("Add dynamo implementation for block data for L2 Caching")
    }

    /**
     * Unconditionally overwrite the entry where the partition key "Property" is equal to the name value of
     * `ServiceMetadata.Properties.MAX_HISTORICAL_BLOCK_HEIGHT` * with attribute "Value" set to the string-ified
     * version of `blockHeight`.
     *
     * @property blockHeight The height to record
     * @return The result of storing [blockHeight]
     */
    override suspend fun writeBlockCheckpoint(blockHeight: Long): WriteResult {
        return try {
            val response: UpdateItemResponse = dynamoClient.updateItem { request: UpdateItemRequest.Builder ->
                updateMaxHistoricalBlockHeight(blockHeight, request)
            }
                .await()
            WriteResult.ok(if (response.hasAttributes()) 1 else 0)
        } catch (e: ConditionalCheckFailedException) {
            WriteResult.empty()
        }
    }
}
