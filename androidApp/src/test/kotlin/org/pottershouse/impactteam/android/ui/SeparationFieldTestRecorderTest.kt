package org.pottershouse.impactteam.android.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.pottershouse.impactteam.state.SeparationLevel

class SeparationFieldTestRecorderTest {
    @Test
    fun `records milestones once in order and marks complete after recovery`() {
        val recorder = SeparationFieldTestRecorder()
        val startedAt = 1_000L

        recorder.start(memberId = "member-a", startedAtEpochMillis = startedAt)
        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.CLEAR, 1_100L)
        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.WARNING, 5_000L)
        recorder.observe(SeparationLevel.WARNING, SeparationLevel.WARNING, 35_000L)
        recorder.observe(SeparationLevel.SERIOUS, SeparationLevel.SERIOUS, 55_000L)
        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.CLEAR, 90_000L)

        val result = recorder.snapshot()
        assertEquals("member-a", result.memberId)
        assertEquals(1_100L, result.withinRangeAtEpochMillis)
        assertEquals(5_000L, result.watchingAtEpochMillis)
        assertEquals(35_000L, result.warningAtEpochMillis)
        assertEquals(55_000L, result.seriousAtEpochMillis)
        assertEquals(90_000L, result.recoveredAtEpochMillis)
        assertTrue(result.isComplete)
    }

    @Test
    fun `does not overwrite first observed milestone and does not recover before serious`() {
        val recorder = SeparationFieldTestRecorder()
        recorder.start(memberId = "member-b", startedAtEpochMillis = 1_000L)

        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.WARNING, 5_000L)
        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.WARNING, 6_000L)
        recorder.observe(SeparationLevel.WARNING, SeparationLevel.WARNING, 35_000L)
        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.CLEAR, 40_000L)

        val result = recorder.snapshot()
        assertEquals(5_000L, result.watchingAtEpochMillis)
        assertEquals(35_000L, result.warningAtEpochMillis)
        assertEquals(null, result.seriousAtEpochMillis)
        assertEquals(null, result.recoveredAtEpochMillis)
        assertFalse(result.isComplete)
    }

    @Test
    fun `reset clears active field test`() {
        val recorder = SeparationFieldTestRecorder()
        recorder.start(memberId = "member-c", startedAtEpochMillis = 1_000L)
        recorder.observe(SeparationLevel.CLEAR, SeparationLevel.WARNING, 2_000L)

        recorder.reset()

        val result = recorder.snapshot()
        assertEquals(null, result.memberId)
        assertEquals(0, result.uniqueGpsFixCount)
        assertFalse(result.isActive)
    }

    @Test
    fun `redacted summary contains no coordinates`() {
        val result = SeparationFieldTestState(
            memberId = "member-d",
            startedAtEpochMillis = 1_000L,
            withinRangeAtEpochMillis = 2_000L,
            watchingAtEpochMillis = 4_000L,
            warningAtEpochMillis = 34_000L,
            seriousAtEpochMillis = 54_000L,
            recoveredAtEpochMillis = 90_000L,
        )

        val summary = result.redactedSummary()

        assertTrue(summary.contains("member-d"))
        assertTrue(summary.contains("Complete"))
        assertTrue(summary.contains("Watching: 3 sec"))
        assertFalse(summary.contains("latitude", ignoreCase = true))
        assertFalse(summary.contains("longitude", ignoreCase = true))
        assertFalse(summary.contains("-17."))
    }

    @Test
    fun `diagnostics count unique fixes without exposing fix ids`() {
        val recorder = SeparationFieldTestRecorder()
        recorder.start(memberId = "member-e", startedAtEpochMillis = 1_000L)

        recorder.observe(
            stableLevel = SeparationLevel.CLEAR,
            rawLevel = SeparationLevel.CLEAR,
            observedAtEpochMillis = 10_000L,
            gpsFixId = "fix-1",
            capturedAtEpochMillis = 9_000L,
            accuracyMeters = 8,
            separationDistanceMeters = 30,
        )
        recorder.observe(
            stableLevel = SeparationLevel.CLEAR,
            rawLevel = SeparationLevel.CLEAR,
            observedAtEpochMillis = 11_000L,
            gpsFixId = "fix-1",
            capturedAtEpochMillis = 9_000L,
            accuracyMeters = 8,
            separationDistanceMeters = 31,
        )
        recorder.observe(
            stableLevel = SeparationLevel.CLEAR,
            rawLevel = SeparationLevel.WARNING,
            observedAtEpochMillis = 20_000L,
            gpsFixId = "fix-2",
            capturedAtEpochMillis = 19_000L,
            accuracyMeters = 6,
            separationDistanceMeters = 205,
        )

        val result = recorder.snapshot()
        assertEquals(2, result.uniqueGpsFixCount)
        assertEquals(1L, result.latestFixAgeSeconds)
        assertEquals(6, result.latestAccuracyMeters)
        assertEquals(205, result.latestSeparationDistanceMeters)

        val summary = result.redactedSummary()
        assertTrue(summary.contains("GPS fixes observed: 2"))
        assertTrue(summary.contains("Latest fix age: 1 sec"))
        assertTrue(summary.contains("Latest accuracy: ±6 m"))
        assertTrue(summary.contains("Latest separation: 205 m"))
        assertFalse(summary.contains("fix-1"))
        assertFalse(summary.contains("fix-2"))
    }
}
