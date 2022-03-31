package io.provenance.aggregate.repository

import io.provenance.eventstream.stream.models.StreamBlock

interface RepositoryBase {
    fun saveBlock(block: StreamBlock)
}
