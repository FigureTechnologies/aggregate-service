package io.provenance.aggregate.service.aws.dynamodb

data class WriteResult(val processed: Int, val unprocessed: Int) {
    companion object {
        val DEFAULT = WriteResult(processed = 0, unprocessed = 0)
    }
}