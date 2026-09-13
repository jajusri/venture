package com.jajusri.venture.feature.transaction.domain.model

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Bounded sender outbox retry/backpressure policy. Durable envelopes are never dropped;
 * retries stay idempotent and partition by company/outbox row rather than a global worker. */
object RelayOutboxRetryPolicy {
    const val MAX_DISPATCH_BATCH = 10
    const val MAX_ATTEMPTS = 8
    private const val INITIAL_DELAY_MS = 500L
    private const val MAX_DELAY_MS = 30_000L
    private const val MULTIPLIER = 2.0
    private const val JITTER_RATIO = 0.2

    fun readyForRetry(attemptCount: Int, lastAttemptAtEpochMillis: Long?, nowEpochMillis: Long, random: Random = Random.Default): Boolean {
        if (attemptCount <= 0) return true
        val last = lastAttemptAtEpochMillis ?: return true
        return nowEpochMillis >= last + delayMillis(attemptCount - 1, random)
    }

    fun delayMillis(attemptIndex: Int, random: Random = Random.Default): Long {
        val exponential = (INITIAL_DELAY_MS * MULTIPLIER.pow(attemptIndex.toDouble())).toLong()
        val capped = min(exponential, MAX_DELAY_MS)
        val jitterSpan = (capped * JITTER_RATIO).toLong()
        val minDelay = (capped - jitterSpan).coerceAtLeast(0)
        val maxDelay = capped + jitterSpan
        return if (maxDelay <= minDelay) minDelay else random.nextLong(minDelay, maxDelay + 1)
    }

    fun attemptsExhausted(attemptCount: Int): Boolean = attemptCount >= MAX_ATTEMPTS
}
