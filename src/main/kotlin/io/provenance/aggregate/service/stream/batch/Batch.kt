package io.provenance.aggregate.service.stream.batch

import io.provenance.aggregate.service.DispatcherProvider
import io.provenance.aggregate.service.logger
import io.provenance.aggregate.service.stream.extractors.Extractor
import io.provenance.aggregate.service.stream.models.StreamBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Batch process blocks.
 */
data class Batch internal constructor(
    val id: BatchId,
    private val extractors: List<Extractor>,
    private val dispatchers: DispatcherProvider
) {
    data class Builder(
        val extractorClassAndArgs: MutableList<Pair<KClass<out Extractor>, Array<out Any>>> = mutableListOf(),
    ) {
        var dispatchers: DispatcherProvider? = null

        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }
        fun withExtractor(extractor: KClass<out Extractor>, vararg args: Any) =
            apply { extractorClassAndArgs.add(Pair(extractor, args)) }

        fun build(): Batch =
            Batch(
                BatchId(), // each batch is assigned a unique ID
                extractorClassAndArgs.mapNotNull { (klass, args) -> klass.primaryConstructor?.call(*args) },
                dispatchers ?: error("dispatchers must be provided")
            )
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    private val log = logger()

    /**
     * Called per block to process and extract data.
     *
     * @param block The block to process.
     */
    suspend fun processBlock(block: StreamBlock) =
        // Generate a map from the class -> result pairs and save it:
        withContext(dispatchers.io()) {
            extractors.map { extractor: Extractor ->
                async {
                    runCatching { extractor.extract(block) }
                        .onFailure { e -> log.error("processing error: $e") }
                }
            }
        }.awaitAll()

    /**
     * Called upon completion of processing all blocks. Results will be aggregated per extractor registered to
     * this batch by way of the `Batch.Builder.withExtractor()` method.
     *
     * The `completeAction` callback will be called per registered extractor class.
     *
     * - If the `beforeComplete` method is implemented for an extractor, it will be called prior to `completeAction`.
     *
     * @param completeAction The action to run upon completion of the batch per extractor.
     */
    suspend fun <T> complete(completeAction: suspend (BatchId, Extractor) -> T): List<T> =
        withContext(dispatchers.io()) {
            extractors.map { extractor ->
                async {
                    extractor.use {
                        it.beforeComplete()
                        completeAction(id, extractor)
                    }
                }
            }
        }.awaitAll()
}