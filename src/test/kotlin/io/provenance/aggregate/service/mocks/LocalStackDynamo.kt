package io.provenance.aggregate.service.mocks

import io.provenance.aggregate.service.aws.dynamodb.AwsDynamo
import io.provenance.aggregate.service.aws.dynamodb.DynamoTable
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

class LocalStackDynamo(
    dynamoClient: DynamoDbAsyncClient,
    blockBatchTable: DynamoTable,
    blockMetadataTable: DynamoTable,
    serviceMetadataTable: DynamoTable
) :
    AwsDynamo(dynamoClient, blockBatchTable, blockMetadataTable, serviceMetadataTable) {

    suspend fun createTables() {
        SERVICE_METADATA_TABLE.createTable().await()
        BLOCK_BATCH_TABLE.createTable().await()
        BLOCK_METADATA_TABLE.createTable().await()
    }

    suspend fun dropTables() {
        SERVICE_METADATA_TABLE.deleteTable().await()
        BLOCK_BATCH_TABLE.deleteTable().await()
        BLOCK_METADATA_TABLE.deleteTable().await()
    }
}