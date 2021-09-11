package io.provenance.aggregate.service.mocks

import io.provenance.aggregate.service.aws.dynamodb.AwsDynamo
import io.provenance.aggregate.service.aws.dynamodb.Table
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

class LocalStackDynamo(dynamoClient: DynamoDbAsyncClient, blockMetadataTable: Table) :
    AwsDynamo(dynamoClient, blockMetadataTable) {

    suspend fun createTable() {
        blockMetadataTable.createTable().await()
    }

    suspend fun dropTable() {
        blockMetadataTable.deleteTable().await()
    }
}