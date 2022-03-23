package io.provenance.aggregate.repository

import io.provenance.aggregate.common.models.StreamBlock

interface RepositoryBase<T> {
    fun save(block: StreamBlock)
    fun saveChanges()
}
