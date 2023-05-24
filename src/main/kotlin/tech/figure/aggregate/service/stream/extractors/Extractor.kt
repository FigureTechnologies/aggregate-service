package tech.figure.aggregate.service.stream.extractors

import tech.figure.aggregate.common.models.block.StreamBlock
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducer
import tech.figure.aggregate.service.stream.kafka.KafkaProducerFactory

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
    suspend fun extract(block: StreamBlock, producer: KafkaProducerFactory?)

    /**
     * Run before the results of the extractor is collected.
     */
    suspend fun beforeComplete() {}
}
