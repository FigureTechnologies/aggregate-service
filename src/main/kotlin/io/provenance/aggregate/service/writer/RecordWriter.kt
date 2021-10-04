package io.provenance.aggregate.service.writer

/**
 * A generic interface for writing a record somehow, to somewhere.
 */
interface RecordWriter {
    fun writeRecord(vararg values: Any?): RecordWriter
}