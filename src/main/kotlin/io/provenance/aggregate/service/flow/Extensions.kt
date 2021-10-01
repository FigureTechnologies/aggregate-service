package io.provenance.aggregate.service.flow.extensions

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.min

/**
 * Cancels the flow upon receipt of a signal from
 *
 * Adapted from https://stackoverflow.com/a/59109105
 *
 * @see https://stackoverflow.com/a/59109105
 */
@OptIn(ExperimentalStdlibApi::class, InternalCoroutinesApi::class, FlowPreview::class)
fun <T> Flow<T>.cancelOnSignal(
    signal: Channel<Unit>
): Flow<T> = flow {
    val outer = this
    try {
        coroutineScope {
            launch {
                signal.receive()
                this@coroutineScope.cancel()
            }

            collect(object : FlowCollector<T> {
                override suspend fun emit(value: T) {
                    outer.emit(value)
                }
            })
        }
    } catch (e: CancellationException) {
        //ignore
    }
}

// Code below is adapted from https://github.com/Kotlin/kotlinx.coroutines/pull/1558

/**
 * Returns a flow of lists each not exceeding the given [size].
 * The last list in the resulting flow may have less elements than the given [size].
 *
 * @param size the number of elements to take in each list, must be positive and can be greater than the number of elements in this flow.
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = chunked(size) { it.toList() }

/**
 * Chunks a flow of elements into flow of lists, each not exceeding the given [size]
 * and applies the given [transform]function to an each.
 *
 * Note that the list passed to the [transform] function is ephemeral and is valid only inside that function.
 * You should not store it or allow it to escape in some way, unless you made a snapshot of it.
 * The last list may have less elements than the given [size].
 *
 * This is more efficient, than using flow.chunked(n).map { ... }
 *
 * @param size the number of elements to take in each list, must be positive and can be greater than the number of elements in this flow.
 */
@OptIn(FlowPreview::class)
fun <T, R> Flow<T>.chunked(size: Int, transform: suspend (List<T>) -> R): Flow<R> {
    require(size > 0) { "Size should be greater than 0, but was $size" }
    return windowed(size, size, true, transform)
}

/**
 * Returns a flow of snapshots of the window of the given [size]
 * sliding along this flow with the given [step], where each
 * snapshot is a list.
 *
 * Several last lists may have less elements than the given [size].
 *
 * Both [size] and [step] must be positive and can be greater than the number of elements in this flow.
 * @param size the number of elements to take in each window
 * @param step the number of elements to move the window forward by on an each step
 * @param partialWindows controls whether or not to keep partial windows in the end if any.
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.windowed(size: Int, step: Int, partialWindows: Boolean): Flow<List<T>> =
    windowed(size, step, partialWindows) { it.toList() }

/**
 * Returns a flow of results of applying the given [transform] function to
 * an each list representing a view over the window of the given [size]
 * sliding along this collection with the given [step].
 *
 * Note that the list passed to the [transform] function is ephemeral and is valid only inside that function.
 * You should not store it or allow it to escape in some way, unless you made a snapshot of it.
 * Several last lists may have less elements than the given [size].
 *
 * This is more efficient, than using flow.windowed(...).map { ... }
 *
 * Both [size] and [step] must be positive and can be greater than the number of elements in this collection.
 * @param size the number of elements to take in each window
 * @param step the number of elements to move the window forward by on an each step.
 * @param partialWindows controls whether or not to keep partial windows in the end if any.
 */
@OptIn(ExperimentalStdlibApi::class, InternalCoroutinesApi::class, FlowPreview::class)
fun <T, R> Flow<T>.windowed(
    size: Int,
    step: Int,
    partialWindows: Boolean,
    transform: suspend (List<T>) -> R
): Flow<R> {
    require(size > 0 && step > 0) { "Size and step should be greater than 0, but was size: $size, step: $step" }

    return flow {
        val buffer = ArrayDeque<T>(size)
        val toDrop = min(step, size)
        val toSkip = max(step - size, 0)
        var skipped = toSkip

        collect(object : FlowCollector<T> {
            override suspend fun emit(value: T) {
                if (toSkip == skipped) buffer.addLast(value)
                else skipped++

                if (buffer.size == size) {
                    emit(transform(buffer))
                    repeat(toDrop) { buffer.removeFirst() }
                    skipped = 0
                }
            }
        })

        while (partialWindows && buffer.isNotEmpty()) {
            emit(transform(buffer))
            repeat(min(toDrop, buffer.size)) { buffer.removeFirst() }
        }
    }
}