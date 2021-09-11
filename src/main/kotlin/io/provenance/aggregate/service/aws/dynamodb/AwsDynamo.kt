package io.provenance.aggregate.service.aws.dynamodb

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import okhttp3.internal.toImmutableList
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

// See https://aws.amazon.com/blogs/developer/introducing-enhanced-dynamodb-client-in-the-aws-sdk-for-java-v2 for usage

open class AwsDynamo(
    private val dynamoClient: DynamoDbAsyncClient,
    private val table: Table
) : AwsDynamoInterface {

    val enhancedClient: DynamoDbEnhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(dynamoClient).build()

    val blockMetadataTableSchema: ImmutableTableSchema<BlockStorageMetadata> =
        TableSchema.fromImmutableClass(BlockStorageMetadata::class.java)

    val blockMetadataTable: DynamoDbAsyncTable<BlockStorageMetadata> =
        enhancedClient.table(table.name, blockMetadataTableSchema)

    override suspend fun getBlockMetadata(blockHeight: Long): BlockStorageMetadata? {
        return blockMetadataTable.getItem(Key.builder().partitionValue(blockHeight).build()).await()
    }

    @OptIn(FlowPreview::class)
    override suspend fun getBlockMetadata(blockHeights: Iterable<Long>): Flow<BlockStorageMetadata> {
        val reader = ReadBatch.builder(BlockStorageMetadata::class.java)
            .mappedTableResource(blockMetadataTable)

        for (blockHeight in blockHeights) {
            reader.addGetItem(Key.builder().partitionValue(blockHeight).build())
        }

        return enhancedClient.batchGetItem { request: BatchGetItemEnhancedRequest.Builder ->
            request.addReadBatch(reader.build()).build()
        }
            .asFlow()
            .flatMapConcat { page: BatchGetResultPage -> page.resultsForTable(blockMetadataTable).asFlow() }
    }

    // Just record block heights for now:
    override suspend fun markBlocks(blockHeights: Iterable<Long>): WriteResult {
        val writer = WriteBatch.builder(BlockStorageMetadata::class.java)
            .mappedTableResource(blockMetadataTable)

        var totalBlockHeights: Int = 0
        for (blockHeight in blockHeights) {
            writer.addPutItem(BlockStorageMetadata(blockHeight = blockHeight))
            totalBlockHeights += 1
        }

        val result: BatchWriteResult = enhancedClient.batchWriteItem { request: BatchWriteItemEnhancedRequest.Builder ->
            request.addWriteBatch(writer.build())
        }.await()

        val unprocessedItems: List<BlockStorageMetadata> =
            result.unprocessedPutItemsForTable(blockMetadataTable).toImmutableList()

        return WriteResult(
            processed = totalBlockHeights - unprocessedItems.size,
            unprocessed = unprocessedItems.size
        )
    }
}