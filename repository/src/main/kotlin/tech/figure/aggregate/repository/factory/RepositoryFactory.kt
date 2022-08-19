package tech.figure.aggregate.repository.factory

import tech.figure.aggregate.common.DBConfig
import tech.figure.aggregate.common.DbTypes.DYNAMODB
import tech.figure.aggregate.common.DbTypes.RAVENDB
import tech.figure.aggregate.repository.RepositoryBase
import tech.figure.aggregate.repository.database.ravendb.RavenDB
import tech.figure.aggregate.repository.database.dynamo.client.DynamoClient

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
