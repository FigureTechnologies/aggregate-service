package io.provenance.aggregate.common.writer

/**
 * A generic interface for writing a record somehow, to somewhere.
 */
interface RecordWriter {
    /**
     * Writes a series of values. The means of writing is determined by the implementing class.
     *
     * @return The implementing class
     */
    fun writeRecord(vararg values: Any?): RecordWriter
}
