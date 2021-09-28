package io.provenance.aggregate.service.stream

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object Backoff {
    fun forAttempt(attempt: Long): Duration = Duration.milliseconds(1000.0 * 2.0.pow(attempt.toDouble()))
    fun forAttempt(attempt: Int): Duration = forAttempt(attempt.toLong())
}