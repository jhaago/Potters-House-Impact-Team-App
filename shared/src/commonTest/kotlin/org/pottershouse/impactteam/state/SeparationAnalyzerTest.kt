package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.LocationPayload
import org.pottershouse.impactteam.protocol.locationEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeparationAnalyzerTest {
    private val analyzer = SeparationAnalyzer()

    @Test
    fun closeTeamIsClear() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82495, 31.03360),
                state("c", -17.82535, 31.03380),
                state("d", -17.82505, 31.03320),
            ),
        )

        assertEquals(setOf(SeparationLevel.CLEAR), assessments.map { it.level }.toSet())
        assertTrue(assessments.all { it.clusterMemberCount == 4 })
    }

    @Test
    fun isolatedMemberBeyondWarningDistanceIsWarning() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82500, 31.03365),
                state("c", -17.82535, 31.03375),
                state("outlier", -17.82338, 31.03350),
            ),
        )

        val outlier = assessments.single { it.memberId == MemberId("outlier") }
        assertEquals(SeparationLevel.WARNING, outlier.level)
        assertTrue(outlier.clusterDistanceMeters != null && outlier.clusterDistanceMeters >= 180.0)
    }

    @Test
    fun isolatedMemberBeyondSeriousDistanceIsSerious() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82500, 31.03365),
                state("c", -17.82535, 31.03375),
                state("outlier", -17.82225, 31.03350),
            ),
        )

        assertEquals(
            SeparationLevel.SERIOUS,
            assessments.single { it.memberId == MemberId("outlier") }.level,
        )
    }

    @Test
    fun nearbyBreakawayPairIsStillSeriousWhenFarFromMainCluster() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82500, 31.03365),
                state("c", -17.82535, 31.03375),
                state("d", -17.82510, 31.03330),
                state("break-1", -17.82220, 31.03350),
                state("break-2", -17.82212, 31.03355),
            ),
        )

        assertEquals(
            setOf(SeparationLevel.SERIOUS),
            assessments.filter { it.memberId.value.startsWith("break-") }.map { it.level }.toSet(),
        )
        assertTrue(
            assessments.filter { it.memberId.value.startsWith("break-") }
                .all { it.nearestNeighborMeters != null && it.nearestNeighborMeters < 30.0 },
        )
    }

    @Test
    fun poorAccuracyMemberIsNotUsedAsSeparationEvidence() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82500, 31.03365),
                state("c", -17.82535, 31.03375),
                state("poor", -17.82100, 31.03350, accuracyMeters = 150.0),
            ),
        )

        assertEquals(
            SeparationLevel.INSUFFICIENT_DATA,
            assessments.single { it.memberId == MemberId("poor") }.level,
        )
        assertEquals(
            setOf(SeparationLevel.CLEAR),
            assessments.filterNot { it.memberId == MemberId("poor") }.map { it.level }.toSet(),
        )
    }

    @Test
    fun staleMemberIsNotUsedAsSeparationEvidence() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82500, 31.03365),
                state("c", -17.82535, 31.03375),
                state("stale", -17.82100, 31.03350, freshness = Freshness.STALE),
            ),
        )

        assertEquals(
            SeparationLevel.INSUFFICIENT_DATA,
            assessments.single { it.memberId == MemberId("stale") }.level,
        )
        assertEquals(
            setOf(SeparationLevel.CLEAR),
            assessments.filterNot { it.memberId == MemberId("stale") }.map { it.level }.toSet(),
        )
    }

    @Test
    fun fewerThanThreeUsableMembersIsInsufficientData() {
        val assessments = analyzer.assess(
            listOf(
                state("a", -17.82520, 31.03350),
                state("b", -17.82500, 31.03365),
                state("stale", -17.82100, 31.03350, freshness = Freshness.STALE),
            ),
        )

        assertEquals(setOf(SeparationLevel.INSUFFICIENT_DATA), assessments.map { it.level }.toSet())
    }

    private fun state(
        member: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double = 8.0,
        freshness: Freshness = Freshness.CURRENT,
    ): MemberTrackingState {
        val envelope = locationEnvelope().copy(
            recordId = RecordId("record-$member"),
            memberId = MemberId(member),
            originDeviceId = DeviceId("device-$member"),
            payload = LocationPayload(
                latitude = latitude,
                longitude = longitude,
                capturedAtEpochMillis = 1_700_000_000_000,
                accuracyMeters = accuracyMeters,
                batteryPercent = 80,
            ),
        )
        return MemberTrackingState(
            memberId = MemberId(member),
            observed = ObservedEnvelope(
                envelope = envelope,
                receivedAtEpochMillis = 1_700_000_100_000,
                arrivalPath = ArrivalPath.DIRECT_PEER,
                suppliedByPeerId = null,
            ),
            freshness = freshness,
        )
    }
}
