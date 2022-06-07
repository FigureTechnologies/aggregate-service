package io.provenance.aggregate.repository.factory

import io.provenance.aggregate.common.DBConfig
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.database.RavenDB
import io.provenance.aggregate.repository.database.dynamo.client.DynamoClient

class RepositoryFactory(
    private val dbConfig: DBConfig
) {

    companion object {
        const val RAVENDB = "ravendb"
        const val DYNAMODB = "dynamodb"
    }

    fun dbInstance(): RepositoryBase? {

        return when(dbConfig.dbType) {
            RAVENDB -> RavenDB(dbConfig.addr, dbConfig.dbName, dbConfig.dbMaxConnections)
            DYNAMODB -> DynamoClient(dbConfig.dynamodb).dynamo()
            else -> null
        }

    }
}
