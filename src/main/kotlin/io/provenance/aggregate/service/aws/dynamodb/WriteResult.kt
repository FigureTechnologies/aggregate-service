package io.provenance.aggregate.service.aws.dynamodb

data class WriteResult(val processed: Int, val unprocessed: List<Long>) {
    companion object {
        fun empty() = WriteResult(processed = 0, unprocessed = emptyList())
    }
}