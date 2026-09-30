package org.pottershouse.impactteam.android.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.state.SeparationLevel

@OptIn(ExperimentalCoroutinesApi::class)
class SeparationFieldTestViewModelTest {
    @Test
    fun `selected member milestones are captured from refreshed rows and reset on stop`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher + testScheduler)
        var now = 1_000L
        var rows = listOf(member("member-a", SeparationLevel.CLEAR, SeparationLevel.CLEAR))
        val telemetry = TrackingProofTelemetryStore()
        val viewModel = TrackingProofViewModel(
            scope = scope,
            nowEpochMillis = { now },
            missingPermissions = { emptySet<TrackingPermission>() },
            startTracking = {},
            stopTracking = {},
            loadMembers = { rows.map { it.state } },
            telemetry = telemetry.state,
        )
        val setup = ProofSetup("trip", "Trip", "team", "leader", "device")

        viewModel.start(setup)
        viewModel.startSeparationFieldTest("member-a")
        scope.testScheduler.runCurrent()

        now = 5_000L
        rows = listOf(member("member-a", SeparationLevel.CLEAR, SeparationLevel.WARNING))
        advanceTimeBy(1_000L)
        scope.testScheduler.runCurrent()

        val active = viewModel.state.value.separationFieldTest
        assertEquals("member-a", active.memberId)
        assertTrue(active.watchingAtEpochMillis != null)

        viewModel.stop()
        val reset = viewModel.state.value.separationFieldTest
        assertFalse(reset.isActive)
        assertEquals(null, reset.memberId)
    }

    private data class MemberFixture(val state: org.pottershouse.impactteam.state.MemberTrackingState)

    private fun member(memberId: String, stable: SeparationLevel, raw: SeparationLevel): MemberFixture =
        throw UnsupportedOperationException("fixture supplied by production-facing integration helper")
}
