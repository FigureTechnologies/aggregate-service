package io.provenance.aggregate.service.writer

/**
 * A sister interface to `RecordWriter`, intended to be implemenented by types that are able to have their state
 * written out as a single, unified "record".
 */
interface Record {
    /**
     * Writes out the type's representation as a record, using the provided `RecordWriter` interface.
     */
    fun write(writer: RecordWriter)

    /**
     * Optionally, implementing types can provide an implementation of how a header (if any) should be written.
     */
    fun writeHeader(writer: RecordWriter) {}
}