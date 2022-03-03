package io.provenance.aggregate.service.test.mocks

import io.provenance.aggregate.common.aws.dynamodb.client.DefaultDynamoClient
import io.provenance.aggregate.common.aws.dynamodb.DynamoTable
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.concurrent.CompletableFuture

class LocalStackDynamoClient(
    private val dynamoClient: DynamoDbAsyncClient,
    private val blockBatchTable: DynamoTable,
    private val blockMetadataTable: DynamoTable,
    private val serviceMetadataTable: DynamoTable,
    private val s3KeyCacheTable: DynamoTable
) :
    DefaultDynamoClient(dynamoClient, blockBatchTable, blockMetadataTable, serviceMetadataTable, s3KeyCacheTable) {

    private fun createServiceMetadataTable(): CompletableFuture<CreateTableResponse> =
        dynamoClient.createTable(
            CreateTableRequest
                .builder()
                .tableName(serviceMetadataTable.name)
                .keySchema(
                    KeySchemaElement
                        .builder()
                        .attributeName("Property")
                        .keyType("HASH")
                        .build()
                )
                .attributeDefinitions(
                    AttributeDefinition
                        .builder()
                        .attributeName("Property")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .provisionedThroughput(
                    ProvisionedThroughput
                        .builder()
                        .readCapacityUnits(1)
                        .writeCapacityUnits(1)
                        .build()
                )
                .build()
        )

    private fun deleteServiceMetadataTable(): CompletableFuture<DeleteTableResponse> =
        dynamoClient.deleteTable(
            DeleteTableRequest.builder().tableName(serviceMetadataTable.name).build()
        )

    suspend fun createTables() {
        createServiceMetadataTable().await()
        BLOCK_BATCH_TABLE.createTable().await()
        BLOCK_METADATA_TABLE.createTable().await()
    }

    suspend fun dropTables() {
        deleteServiceMetadataTable().await()
        BLOCK_BATCH_TABLE.deleteTable().await()
        BLOCK_METADATA_TABLE.deleteTable().await()
    }
}
