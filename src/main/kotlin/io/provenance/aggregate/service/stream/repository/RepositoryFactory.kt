package io.provenance.aggregate.service.stream.repository

import io.provenance.aggregate.common.DBConfig
import io.provenance.aggregate.service.stream.repository.db.CockroachDB
import io.provenance.aggregate.service.stream.repository.db.DBInterface
import io.provenance.aggregate.service.stream.repository.db.RavenDB

class RepositoryFactory(
    private val dbConfig: DBConfig
) {

    fun dbInstance(): DBInterface<Any>? {
//        return when (dbConfig.dbType) {
//            "ravendb" -> RavenDB(dbConfig.addr, dbConfig.dbName)
//            "cockroachdb" -> CockroachDB(dbConfig.addr, dbConfig.dbName, dbConfig.username, dbConfig.password)
//            else -> null
//        }
        return  RavenDB(dbConfig.addr, dbConfig.dbName)
    }

}
