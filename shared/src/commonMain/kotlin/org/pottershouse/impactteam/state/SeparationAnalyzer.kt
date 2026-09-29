package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.MemberId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SeparationLevel {
    INSUFFICIENT_DATA,
    CLEAR,
    WARNING,
    SERIOUS,
}

data class SeparationPolicy(
    val warningDistanceMeters: Double = 180.0,
    val seriousDistanceMeters: Double = 275.0,
    val maxAccuracyMeters: Double = 75.0,
    val minUsableMembers: Int = 3,
    val clusterLinkDistanceMeters: Double = 180.0,
) {
    init {
        require(warningDistanceMeters > 0.0)
        require(seriousDistanceMeters >= warningDistanceMeters)
        require(maxAccuracyMeters > 0.0)
        require(minUsableMembers >= 2)
        require(clusterLinkDistanceMeters > 0.0)
    }
}

data class SeparationAssessment(
    val memberId: MemberId,
    val level: SeparationLevel,
    val nearestNeighborMeters: Double?,
    val clusterDistanceMeters: Double?,
    val clusterMemberCount: Int,
)

class SeparationAnalyzer(
    private val policy: SeparationPolicy = SeparationPolicy(),
) {
    fun assess(states: List<MemberTrackingState>): List<SeparationAssessment> {
        if (states.isEmpty()) return emptyList()

        val usable = states.filter(::isUsable)
        if (usable.size < policy.minUsableMembers) {
            return states.map {
                SeparationAssessment(
                    memberId = it.memberId,
                    level = SeparationLevel.INSUFFICIENT_DATA,
                    nearestNeighborMeters = null,
                    clusterDistanceMeters = null,
                    clusterMemberCount = usable.size,
                )
            }
        }

        val mainCluster = mainCluster(usable)
        val medoid = medoid(mainCluster)
        val usableMemberIds = usable.mapTo(mutableSetOf()) { it.memberId }

        return states.map { state ->
            if (state.memberId !in usableMemberIds || !isUsable(state)) {
                return@map SeparationAssessment(
                    memberId = state.memberId,
                    level = SeparationLevel.INSUFFICIENT_DATA,
                    nearestNeighborMeters = null,
                    clusterDistanceMeters = null,
                    clusterMemberCount = mainCluster.size,
                )
            }

            val nearestNeighborMeters = usable
                .asSequence()
                .filter { it.memberId != state.memberId }
                .map { distanceMeters(state, it) }
                .minOrNull()
            val clusterDistanceMeters = distanceMeters(state, medoid)
            val evidenceDistanceMeters = maxOf(nearestNeighborMeters ?: 0.0, clusterDistanceMeters)
            val level = when {
                evidenceDistanceMeters >= policy.seriousDistanceMeters -> SeparationLevel.SERIOUS
                evidenceDistanceMeters >= policy.warningDistanceMeters -> SeparationLevel.WARNING
                else -> SeparationLevel.CLEAR
            }

            SeparationAssessment(
                memberId = state.memberId,
                level = level,
                nearestNeighborMeters = nearestNeighborMeters,
                clusterDistanceMeters = clusterDistanceMeters,
                clusterMemberCount = mainCluster.size,
            )
        }
    }

    private fun isUsable(state: MemberTrackingState): Boolean {
        if (state.freshness == Freshness.STALE) return false
        val payload = state.observed.envelope.payload
        return payload.accuracyMeters.isFinite() &&
            payload.accuracyMeters in 0.0..policy.maxAccuracyMeters &&
            payload.latitude.isFinite() &&
            payload.latitude in -90.0..90.0 &&
            payload.longitude.isFinite() &&
            payload.longitude in -180.0..180.0
    }

    private fun mainCluster(states: List<MemberTrackingState>): List<MemberTrackingState> {
        val visited = BooleanArray(states.size)
        val components = mutableListOf<List<MemberTrackingState>>()

        for (start in states.indices) {
            if (visited[start]) continue

            val queue = mutableListOf(start)
            val component = mutableListOf<MemberTrackingState>()
            visited[start] = true
            var cursor = 0

            while (cursor < queue.size) {
                val currentIndex = queue[cursor++]
                val current = states[currentIndex]
                component += current

                for (candidateIndex in states.indices) {
                    if (visited[candidateIndex]) continue
                    if (distanceMeters(current, states[candidateIndex]) <= policy.clusterLinkDistanceMeters) {
                        visited[candidateIndex] = true
                        queue += candidateIndex
                    }
                }
            }

            components += component
        }

        return components.sortedWith(
            compareByDescending<List<MemberTrackingState>> { it.size }
                .thenBy(::pairwiseDistanceTotal)
                .thenBy { component -> component.map { it.memberId.value }.sorted().joinToString("|") },
        ).first()
    }

    private fun medoid(states: List<MemberTrackingState>): MemberTrackingState =
        states.minWithOrNull(
            compareBy<MemberTrackingState> { candidate ->
                states.sumOf { other -> distanceMeters(candidate, other) }
            }.thenBy { it.memberId.value },
        ) ?: error("Cannot calculate a medoid for an empty cluster")

    private fun pairwiseDistanceTotal(states: List<MemberTrackingState>): Double {
        var total = 0.0
        for (left in states.indices) {
            for (right in left + 1 until states.size) {
                total += distanceMeters(states[left], states[right])
            }
        }
        return total
    }

    private fun distanceMeters(
        first: MemberTrackingState,
        second: MemberTrackingState,
    ): Double {
        val firstPayload = first.observed.envelope.payload
        val secondPayload = second.observed.envelope.payload
        val firstLatitude = firstPayload.latitude.toRadians()
        val secondLatitude = secondPayload.latitude.toRadians()
        val latitudeDelta = (secondPayload.latitude - firstPayload.latitude).toRadians()
        val longitudeDelta = (secondPayload.longitude - firstPayload.longitude).toRadians()
        val latitudeSin = sin(latitudeDelta / 2.0)
        val longitudeSin = sin(longitudeDelta / 2.0)
        val haversine = (
            latitudeSin * latitudeSin +
                cos(firstLatitude) * cos(secondLatitude) * longitudeSin * longitudeSin
            ).coerceIn(0.0, 1.0)
        val centralAngle = 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
        return EARTH_RADIUS_METERS * centralAngle
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
