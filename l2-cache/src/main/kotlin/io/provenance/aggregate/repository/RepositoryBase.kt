package io.provenance.aggregate.repository

import io.provenance.aggregate.common.models.StreamBlock

interface RepositoryBase<T> {
    fun saveBlock(block: StreamBlock)
    fun saveChanges()
}
