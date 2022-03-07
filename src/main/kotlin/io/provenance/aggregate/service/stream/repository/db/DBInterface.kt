package io.provenance.aggregate.service.stream.repository.db

interface DBInterface<T> {

    fun save(hash: String?, vararg data: T?)

    fun saveChanges()

}


