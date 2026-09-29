package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.MemberId

data class SeparationMonitorPolicy(
    val warningSustainMillis: Long = 30_000,
    val seriousSustainMillis: Long = 20_000,
    val movingAwaySeriousSustainMillis: Long = 10_000,
    val clearSustainMillis: Long = 30_000,
    val clearDistanceMeters: Double = 140.0,
    val movingAwayDeltaMeters: Double = 40.0,
    val movingAwayWindowMillis: Long = 30_000,
    val minimumTrendDurationMillis: Long = 5_000,
) {
    init {
        require(warningSustainMillis >= 0)
        require(seriousSustainMillis >= 0)
        require(movingAwaySeriousSustainMillis >= 0)
        require(clearSustainMillis >= 0)
        require(clearDistanceMeters >= 0.0)
        require(movingAwayDeltaMeters > 0.0)
        require(movingAwayWindowMillis > 0)
        require(minimumTrendDurationMillis >= 0)
        require(minimumTrendDurationMillis <= movingAwayWindowMillis)
    }
}

data class MonitoredSeparationAssessment(
    val memberId: MemberId,
    val level: SeparationLevel,
    val rawLevel: SeparationLevel,
    val evidenceDistanceMeters: Double?,
    val movingAway: Boolean,
)

class SeparationMonitor(
    private val policy: SeparationMonitorPolicy = SeparationMonitorPolicy(),
) {
    private val memberStates = mutableMapOf<MemberId, MemberState>()

    fun update(
        assessments: List<SeparationAssessment>,
        nowEpochMillis: Long,
    ): List<MonitoredSeparationAssessment> = assessments.map { assessment ->
        val state = memberStates.getOrPut(assessment.memberId) { MemberState() }
        val evidenceDistance = assessment.evidenceDistanceMeters()

        if (assessment.level == SeparationLevel.INSUFFICIENT_DATA) {
            state.resetPendingEvidence()
            return@map MonitoredSeparationAssessment(
                memberId = assessment.memberId,
                level = if (state.activeLevel.isAlert()) state.activeLevel else SeparationLevel.INSUFFICIENT_DATA,
                rawLevel = assessment.level,
                evidenceDistanceMeters = evidenceDistance,
                movingAway = false,
            )
        }

        if (evidenceDistance != null) {
            state.recordDistance(nowEpochMillis, evidenceDistance, policy.movingAwayWindowMillis)
        }
        val movingAway = state.isMovingAway(nowEpochMillis, evidenceDistance, policy)

        when (assessment.level) {
            SeparationLevel.CLEAR -> handleClear(state, evidenceDistance, nowEpochMillis)
            SeparationLevel.WARNING -> handleWarning(state, nowEpochMillis)
            SeparationLevel.SERIOUS -> handleSerious(state, movingAway, nowEpochMillis)
            SeparationLevel.INSUFFICIENT_DATA -> Unit
        }

        MonitoredSeparationAssessment(
            memberId = assessment.memberId,
            level = state.activeLevel,
            rawLevel = assessment.level,
            evidenceDistanceMeters = evidenceDistance,
            movingAway = movingAway,
        )
    }

    private fun handleClear(
        state: MemberState,
        evidenceDistanceMeters: Double?,
        nowEpochMillis: Long,
    ) {
        state.concernSinceEpochMillis = null
        state.seriousSinceEpochMillis = null

        if (!state.activeLevel.isAlert()) {
            state.activeLevel = SeparationLevel.CLEAR
            state.clearSinceEpochMillis = null
            return
        }

        if (evidenceDistanceMeters == null || evidenceDistanceMeters > policy.clearDistanceMeters) {
            state.clearSinceEpochMillis = null
            return
        }

        val clearSince = state.clearSinceEpochMillis ?: nowEpochMillis.also {
            state.clearSinceEpochMillis = it
        }
        if (elapsedSince(clearSince, nowEpochMillis) >= policy.clearSustainMillis) {
            state.activeLevel = SeparationLevel.CLEAR
            state.clearSinceEpochMillis = null
            state.distanceSamples.clear()
        }
    }

    private fun handleWarning(
        state: MemberState,
        nowEpochMillis: Long,
    ) {
        state.clearSinceEpochMillis = null
        state.seriousSinceEpochMillis = null
        val concernSince = state.concernSinceEpochMillis ?: nowEpochMillis.also {
            state.concernSinceEpochMillis = it
        }

        if (!state.activeLevel.isAlert() && elapsedSince(concernSince, nowEpochMillis) >= policy.warningSustainMillis) {
            state.activeLevel = SeparationLevel.WARNING
        }
    }

    private fun handleSerious(
        state: MemberState,
        movingAway: Boolean,
        nowEpochMillis: Long,
    ) {
        state.clearSinceEpochMillis = null
        val concernSince = state.concernSinceEpochMillis ?: nowEpochMillis.also {
            state.concernSinceEpochMillis = it
        }
        val seriousSince = state.seriousSinceEpochMillis ?: nowEpochMillis.also {
            state.seriousSinceEpochMillis = it
        }

        val sustainedSerious = elapsedSince(seriousSince, nowEpochMillis) >= policy.seriousSustainMillis
        val movingAwaySerious = movingAway &&
            elapsedSince(concernSince, nowEpochMillis) >= policy.movingAwaySeriousSustainMillis

        when {
            sustainedSerious || movingAwaySerious -> state.activeLevel = SeparationLevel.SERIOUS
            !state.activeLevel.isAlert() &&
                elapsedSince(concernSince, nowEpochMillis) >= policy.warningSustainMillis -> {
                state.activeLevel = SeparationLevel.WARNING
            }
        }
    }

    private fun elapsedSince(sinceEpochMillis: Long, nowEpochMillis: Long): Long =
        (nowEpochMillis - sinceEpochMillis).coerceAtLeast(0)

    private fun SeparationAssessment.evidenceDistanceMeters(): Double? =
        listOfNotNull(nearestNeighborMeters, clusterDistanceMeters).maxOrNull()

    private fun SeparationLevel.isAlert(): Boolean =
        this == SeparationLevel.WARNING || this == SeparationLevel.SERIOUS

    private data class DistanceSample(
        val atEpochMillis: Long,
        val distanceMeters: Double,
    )

    private class MemberState {
        var activeLevel: SeparationLevel = SeparationLevel.CLEAR
        var concernSinceEpochMillis: Long? = null
        var seriousSinceEpochMillis: Long? = null
        var clearSinceEpochMillis: Long? = null
        val distanceSamples = mutableListOf<DistanceSample>()

        fun recordDistance(
            nowEpochMillis: Long,
            distanceMeters: Double,
            windowMillis: Long,
        ) {
            distanceSamples += DistanceSample(nowEpochMillis, distanceMeters)
            val oldestAllowed = nowEpochMillis - windowMillis
            while (distanceSamples.isNotEmpty() && distanceSamples.first().atEpochMillis < oldestAllowed) {
                distanceSamples.removeAt(0)
            }
        }

        fun isMovingAway(
            nowEpochMillis: Long,
            currentDistanceMeters: Double?,
            policy: SeparationMonitorPolicy,
        ): Boolean {
            if (currentDistanceMeters == null) return false
            val comparison = distanceSamples.firstOrNull {
                nowEpochMillis - it.atEpochMillis >= policy.minimumTrendDurationMillis
            } ?: return false
            return currentDistanceMeters - comparison.distanceMeters >= policy.movingAwayDeltaMeters
        }

        fun resetPendingEvidence() {
            concernSinceEpochMillis = null
            seriousSinceEpochMillis = null
            clearSinceEpochMillis = null
            distanceSamples.clear()
        }
    }
}
