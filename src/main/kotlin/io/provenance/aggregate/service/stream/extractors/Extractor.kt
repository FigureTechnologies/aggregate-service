package io.provenance.aggregate.service.stream.extractors

import io.provenance.aggregate.service.stream.models.StreamBlock

/**
 * An extractor, as the name implies, is responsible for extracting a subset of data from a stream block, formatting it,
 * and optionally transforming it, before writing the new output to an arbitrary data target.
 */
interface Extractor {
    /**
     * A required name to assign this extractor.
     */
    val name: String

    /**
     * The output type of the extractor.
     *
     * @see [OutputType]
     * @returns The type of output written by extractor implementation.
     */
    fun output(): OutputType

    /**
     * Determines if output should be written.
     */
    fun shouldOutput(): Boolean

    /**
     * Performs extraction of a subset of data from a stream block.
     *
     * @property block The stream block to process.
     */
    suspend fun extract(block: StreamBlock)

    // Optional methods:

    /**
     * Run before the results of the extractor is collected.
     */
    suspend fun beforeComplete() {}
}