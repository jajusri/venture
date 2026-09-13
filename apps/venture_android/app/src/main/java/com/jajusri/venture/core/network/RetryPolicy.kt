package com.jajusri.venture.core.network

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff policy for idempotent Connector requests (typically GET).
 *
 * Do not apply this blindly to non-idempotent mutations.
 */
data class RetryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MILLIS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    val retryOn: (NetworkError) -> Boolean = { it.isRetryable() },
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1." }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must be >= 0." }
        require(maxDelayMillis >= initialDelayMillis) {
            "maxDelayMillis must be >= initialDelayMillis."
        }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0." }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be in [0.0, 1.0]." }
    }

    /**
     * Delay before the attempt at [attemptIndex] (0-based) after a failure.
     * Returns `null` when no further retries remain.
     */
    fun delayBeforeRetryMillis(attemptIndex: Int, random: Random = Random.Default): Long? {
        if (attemptIndex + 1 >= maxAttempts) return null
        val exponential = initialDelayMillis * multiplier.pow(attemptIndex.toDouble())
        val capped = min(exponential, maxDelayMillis.toDouble())
        if (jitterRatio == 0.0) return capped.toLong()
        val jitterSpan = capped * jitterRatio
        val minDelay = (capped - jitterSpan).coerceAtLeast(0.0)
        val maxDelay = capped + jitterSpan
        if (maxDelay <= minDelay) return minDelay.toLong()
        return random.nextDouble(minDelay, maxDelay).toLong()
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_INITIAL_DELAY_MILLIS = 300L
        const val DEFAULT_MAX_DELAY_MILLIS = 5_000L
        const val DEFAULT_MULTIPLIER = 2.0
        const val DEFAULT_JITTER_RATIO = 0.2

        /** Conservative defaults for idempotent Connector GETs. */
        val Default: RetryPolicy = RetryPolicy()

        /** No retries — single attempt only. */
        val None: RetryPolicy = RetryPolicy(maxAttempts = 1)
    }
}

/**
 * Executes [block], retrying according to [policy] when the result is a retryable failure.
 */
suspend fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy.Default,
    random: Random = Random.Default,
    block: suspend () -> ApiResult<T>,
): ApiResult<T> {
    var attempt = 0
    while (true) {
        when (val result = block()) {
            is ApiResult.Success -> return result
            is ApiResult.Failure -> {
                val delayMillis = policy.delayBeforeRetryMillis(attempt, random)
                if (delayMillis == null || !policy.retryOn(result.error)) {
                    return result
                }
                delay(delayMillis)
                attempt += 1
            }
        }
    }
}
