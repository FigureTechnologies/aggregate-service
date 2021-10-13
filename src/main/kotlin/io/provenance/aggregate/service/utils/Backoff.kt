package io.provenance.aggregate.service.utils

import java.util.*
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

const val BASE_WAIT_MILLISECONDS: Double = 1000.0

/**
 * Generates a Duration for implementing a backoff wait scheme.
 *
 * Backoff waits are computed as a function of:
 *
 * - an attempt number
 * - a base wait time in milliseconds
 * - if jitter should be used. If yes, then up to 25% of the original wait time will be added or subtracted from
 *   the final wait
 *
 * The calculation is basically
 *
 *   max(0, base * (2 ^ attempt) +/- (0 to 25% original amount))
 */
fun backoffMillis(attempt: Long, base: Double = BASE_WAIT_MILLISECONDS, jitter: Boolean = true): Double {
    var ms = base * 2.0.pow(attempt.toDouble())
    if (jitter) {
        val factor = ((Random().nextDouble() * 2.0) - 1.0) // [-1.0, 1.0]
        ms += (ms * factor * 0.25)
    }
    return maxOf(0.0, ms)
}

@OptIn(ExperimentalTime::class)
fun backoff(attempt: Long, base: Double = BASE_WAIT_MILLISECONDS, jitter: Boolean = true): Duration =
    Duration.milliseconds(backoffMillis(attempt = attempt, base = base, jitter = jitter))

@OptIn(ExperimentalTime::class)
fun backoff(attempt: Int, base: Double = BASE_WAIT_MILLISECONDS, jitter: Boolean = true): Duration =
    backoff(attempt.toLong(), base = base, jitter = jitter)