package io.provenance.aggregate.service.stream.repository.db

import java.sql.Connection
import java.sql.DriverManager

class CockroachDB(
    private val addr: String?,
    private val database: String?,
    private val username: String?,
    private val password: String?
): DBInterface<Any> {

    val conn: Connection = DriverManager.getConnection("jdbc:postgres://${addr}/${database}", username, password)

    override fun save(hash: String?, vararg data: Any?) {


        TODO("Not yet implemented")
    }

    override fun saveChanges() {
        TODO("Not yet implemented")
    }
}
