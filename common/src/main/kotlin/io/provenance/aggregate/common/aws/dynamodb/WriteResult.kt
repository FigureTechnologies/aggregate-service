package io.provenance.aggregate.common.aws.dynamodb

/**
 * Represents the result of a DynamoDB batch write operation.
 *
 * @property processed The number of items in the batch that were successfully stored.
 * @property unprocessed A list of block heights that failed to be stored.
 */
data class WriteResult(val processed: Int, val unprocessed: List<Long>) {
    companion object {
        fun ok() = WriteResult(processed = 1, unprocessed = emptyList())
        fun ok(n: Int) = WriteResult(processed = n, unprocessed = emptyList())
        fun empty() = WriteResult(processed = 0, unprocessed = emptyList())
    }
}
