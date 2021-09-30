package io.provenance.aggregate.service.aws.dynamodb

data class WriteResult(val processed: Int, val unprocessed: List<Long>) {
    companion object {
        fun ok() = WriteResult(processed = 1, unprocessed = emptyList())
        fun ok(n: Int) = WriteResult(processed = n, unprocessed = emptyList())
        fun empty() = WriteResult(processed = 0, unprocessed = emptyList())
    }
}