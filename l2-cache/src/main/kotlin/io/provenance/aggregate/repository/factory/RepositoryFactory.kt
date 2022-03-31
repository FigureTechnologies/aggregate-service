package io.provenance.aggregate.repository.factory

import io.provenance.aggregate.common.DBConfig
import io.provenance.aggregate.repository.RepositoryBase
import io.provenance.aggregate.repository.database.RavenDB

class RepositoryFactory(
    private val dbConfig: DBConfig
) {
    fun dbInstance(): RepositoryBase {
        /**
         * If we want to support other types of NoSQL database we
         * can wrap with a when and select which database based on
         * the DBConfig type.
         *
         * RavenDB is only supported db at the moment.
         */
        return RavenDB(dbConfig.addr, dbConfig.dbName, dbConfig.dbMaxConnections)
    }
}
