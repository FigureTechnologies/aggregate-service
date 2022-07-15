package io.provenance.aggregate.repository.factory

import io.provenance.aggregate.common.DBConfig
import io.provenance.aggregate.common.DbTypes.DYNAMODB
import io.provenance.aggregate.common.DbTypes.RAVENDB
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.database.ravendb.RavenDB
import io.provenance.aggregate.repository.database.dynamo.client.DynamoClient

class RepositoryFactory(
    private val dbConfig: DBConfig
) {
    fun dbInstance(): RepositoryBase {
        return when(dbConfig.dbType) {
            RAVENDB -> RavenDB(dbConfig.addr, dbConfig.dbName, dbConfig.dbMaxConnections)
            DYNAMODB -> DynamoClient(dbConfig.dynamodb).dynamo()
        }
    }
}
