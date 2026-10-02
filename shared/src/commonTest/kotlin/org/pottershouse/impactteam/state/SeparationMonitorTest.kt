package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeparationMonitorTest {
    @Test
    fun briefWarningSpikeDoesNotRaiseOperationalWarning() {
        val monitor = SeparationMonitor()

        assertEquals(SeparationLevel.CLEAR, monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 0).single().level)
        assertEquals(SeparationLevel.CLEAR, monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 205.0)), 20_000).single().level)
        assertEquals(SeparationLevel.CLEAR, monitor.update(listOf(assessment("a", SeparationLevel.CLEAR, 120.0)), 21_000).single().level)

        assertEquals(SeparationLevel.CLEAR, monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 60_000).single().level)
        assertEquals(SeparationLevel.WARNING, monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 90_000).single().level)
    }

    @Test
    fun seriousDistanceRequiresSustainedEvidenceWhenNotMovingAway() {
        val monitor = SeparationMonitor()

        val first = monitor.update(listOf(assessment("a", SeparationLevel.SERIOUS, 290.0)), 0).single()
        val midway = monitor.update(listOf(assessment("a", SeparationLevel.SERIOUS, 292.0)), 10_000).single()
        val sustained = monitor.update(listOf(assessment("a", SeparationLevel.SERIOUS, 291.0)), 20_000).single()

        assertEquals(SeparationLevel.CLEAR, first.level)
        assertEquals(SeparationLevel.CLEAR, midway.level)
        assertFalse(midway.movingAway)
        assertEquals(SeparationLevel.SERIOUS, sustained.level)
    }

    @Test
    fun seriousDistanceEscalatesEarlyWhenMemberIsClearlyMovingAway() {
        val monitor = SeparationMonitor()

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 190.0)), 0)
        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 225.0)), 5_000)
        val result = monitor.update(listOf(assessment("a", SeparationLevel.SERIOUS, 290.0)), 10_000).single()

        assertTrue(result.movingAway)
        assertEquals(SeparationLevel.SERIOUS, result.level)
    }

    @Test
    fun activeWarningDoesNotClearInsideHysteresisBand() {
        val monitor = SeparationMonitor()

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 0)
        assertEquals(
            SeparationLevel.WARNING,
            monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 30_000).single().level,
        )

        assertEquals(
            SeparationLevel.WARNING,
            monitor.update(listOf(assessment("a", SeparationLevel.CLEAR, 170.0)), 31_000).single().level,
        )
        assertEquals(
            SeparationLevel.WARNING,
            monitor.update(listOf(assessment("a", SeparationLevel.CLEAR, 130.0)), 40_000).single().level,
        )
        assertEquals(
            SeparationLevel.WARNING,
            monitor.update(listOf(assessment("a", SeparationLevel.CLEAR, 130.0)), 69_999).single().level,
        )
        assertEquals(
            SeparationLevel.CLEAR,
            monitor.update(listOf(assessment("a", SeparationLevel.CLEAR, 130.0)), 70_000).single().level,
        )
    }

    @Test
    fun insufficientDataDoesNotSilentlyClearAnActiveWarning() {
        val monitor = SeparationMonitor()

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 0)
        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 30_000)

        val result = monitor.update(listOf(assessment("a", SeparationLevel.INSUFFICIENT_DATA, null)), 31_000).single()

        assertEquals(SeparationLevel.WARNING, result.level)
        assertEquals(SeparationLevel.INSUFFICIENT_DATA, result.rawLevel)
        assertEquals(null, result.evidenceDistanceMeters)
    }

    @Test
    fun insufficientDataIsReportedWhenNoAlertIsActive() {
        val monitor = SeparationMonitor()

        val result = monitor.update(listOf(assessment("a", SeparationLevel.INSUFFICIENT_DATA, null)), 0).single()

        assertEquals(SeparationLevel.INSUFFICIENT_DATA, result.level)
        assertEquals(SeparationLevel.INSUFFICIENT_DATA, result.rawLevel)
    }

    @Test
    fun sameGpsFixCannotSatisfyWarningSustain() {
        val monitor = SeparationMonitor()
        val fix = evidence("fix-1")

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 210.0)), 0, fix)
        val repeated = monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 210.0)), 60_000, fix).single()

        assertEquals(SeparationLevel.CLEAR, repeated.level)
        assertEquals(SeparationLevel.WARNING, repeated.rawLevel)
    }

    @Test
    fun newGpsFixCanConfirmSustainedWarning() {
        val monitor = SeparationMonitor()

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 210.0)), 0, evidence("fix-1"))
        val confirmed = monitor.update(
            listOf(assessment("a", SeparationLevel.WARNING, 212.0)),
            31_000,
            evidence("fix-2"),
        ).single()

        assertEquals(SeparationLevel.WARNING, confirmed.level)
    }

    @Test
    fun repeatedGpsFixCannotCreateMovingAwayTrend() {
        val monitor = SeparationMonitor()

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 190.0)), 0, evidence("fix-1"))
        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 225.0)), 5_000, evidence("fix-2"))
        val repeated = monitor.update(
            listOf(assessment("a", SeparationLevel.SERIOUS, 290.0)),
            10_000,
            evidence("fix-2"),
        ).single()

        assertFalse(repeated.movingAway)
        assertEquals(SeparationLevel.CLEAR, repeated.level)
    }

    @Test
    fun recoveryRequiresNewClearGpsFix() {
        val monitor = SeparationMonitor()

        monitor.update(listOf(assessment("a", SeparationLevel.WARNING, 200.0)), 0, evidence("fix-1"))
        assertEquals(
            SeparationLevel.WARNING,
            monitor.update(
                listOf(assessment("a", SeparationLevel.WARNING, 200.0)),
                30_000,
                evidence("fix-2"),
            ).single().level,
        )
        monitor.update(listOf(assessment("a", SeparationLevel.CLEAR, 130.0)), 40_000, evidence("fix-3"))

        assertEquals(
            SeparationLevel.WARNING,
            monitor.update(
                listOf(assessment("a", SeparationLevel.CLEAR, 130.0)),
                70_000,
                evidence("fix-3"),
            ).single().level,
        )
        assertEquals(
            SeparationLevel.CLEAR,
            monitor.update(
                listOf(assessment("a", SeparationLevel.CLEAR, 130.0)),
                71_000,
                evidence("fix-4"),
            ).single().level,
        )
    }

    private fun evidence(recordId: String) = mapOf(MemberId("a") to RecordId(recordId))

    private fun assessment(
        member: String,
        level: SeparationLevel,
        evidenceDistanceMeters: Double?,
    ) = SeparationAssessment(
        memberId = MemberId(member),
        level = level,
        nearestNeighborMeters = evidenceDistanceMeters,
        clusterDistanceMeters = evidenceDistanceMeters,
        clusterMemberCount = 4,
    )
}
