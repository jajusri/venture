package com.jajusri.venture.feature.transaction.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RelayOutboxRetryPolicyTest {
  @Test
  fun `backoff grows with jitter and blocks immediate retry storms`() {
    val random = Random(1)
    val first = RelayOutboxRetryPolicy.delayMillis(0, random)
    val second = RelayOutboxRetryPolicy.delayMillis(1, random)
    assertTrue(first in 400..600)
    assertTrue(second > first)
    val lastAttempt = 1_000L
    assertFalse(RelayOutboxRetryPolicy.readyForRetry(1, lastAttempt, lastAttempt, random))
    assertTrue(RelayOutboxRetryPolicy.readyForRetry(1, lastAttempt, lastAttempt + 30_001, random))
  }

  @Test
  fun `bounded dispatch batch and attempt cap are fixed`() {
    assertTrue(RelayOutboxRetryPolicy.MAX_DISPATCH_BATCH <= 25)
    assertTrue(RelayOutboxRetryPolicy.attemptsExhausted(RelayOutboxRetryPolicy.MAX_ATTEMPTS))
    assertFalse(RelayOutboxRetryPolicy.attemptsExhausted(RelayOutboxRetryPolicy.MAX_ATTEMPTS - 1))
  }
}
