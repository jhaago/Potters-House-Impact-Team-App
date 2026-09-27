package org.pottershouse.impactteam.state

import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessPolicyTest {
    private val policy = FreshnessPolicy()
    private val receivedAt = 1_000_000L

    @Test
    fun currentThroughThirtySeconds() {
        assertEquals(Freshness.CURRENT, policy.classify(receivedAt, receivedAt))
        assertEquals(Freshness.CURRENT, policy.classify(receivedAt, receivedAt + 30_000))
    }

    @Test
    fun becomingStaleAfterThirtyThroughOneHundredTwentySeconds() {
        assertEquals(Freshness.BECOMING_STALE, policy.classify(receivedAt, receivedAt + 30_001))
        assertEquals(Freshness.BECOMING_STALE, policy.classify(receivedAt, receivedAt + 120_000))
    }

    @Test
    fun staleAfterOneHundredTwentySeconds() {
        assertEquals(Freshness.STALE, policy.classify(receivedAt, receivedAt + 120_001))
    }

    @Test
    fun negativeAgeFromClockCorrectionIsClampedToZero() {
        assertEquals(Freshness.CURRENT, policy.classify(receivedAt, receivedAt - 10_000))
    }
}
