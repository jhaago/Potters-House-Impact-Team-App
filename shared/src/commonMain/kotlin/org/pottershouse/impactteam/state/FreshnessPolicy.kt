package org.pottershouse.impactteam.state

enum class Freshness {
    CURRENT,
    BECOMING_STALE,
    STALE,
}

class FreshnessPolicy(
    private val currentThroughMillis: Long = 30_000,
    private val becomingStaleThroughMillis: Long = 120_000,
) {
    init {
        require(currentThroughMillis >= 0)
        require(becomingStaleThroughMillis >= currentThroughMillis)
    }

    fun classify(receivedAtEpochMillis: Long, nowEpochMillis: Long): Freshness {
        val ageMillis = (nowEpochMillis - receivedAtEpochMillis).coerceAtLeast(0)
        return when {
            ageMillis <= currentThroughMillis -> Freshness.CURRENT
            ageMillis <= becomingStaleThroughMillis -> Freshness.BECOMING_STALE
            else -> Freshness.STALE
        }
    }
}
