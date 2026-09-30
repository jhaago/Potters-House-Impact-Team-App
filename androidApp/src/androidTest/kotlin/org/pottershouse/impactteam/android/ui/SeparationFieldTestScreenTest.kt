package org.pottershouse.impactteam.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TripId

class SeparationFieldTestScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun trackingScreenShowsFieldTestCardAndStartsSelectedMember() {
        var startedMember: String? = null
        val state = activeState().copy(
            members = listOf(
                memberRow("member-a"),
                memberRow("member-b"),
            ),
        )

        compose.setContent {
            MaterialTheme {
                TrackingProofScreen(
                    state = state,
                    onSetupChanged = {},
                    onStart = {},
                    onStop = {},
                    onRequestPermissions = {},
                    onCopyDiagnostics = {},
                    onStartSeparationFieldTest = { startedMember = it },
                    onResetSeparationFieldTest = {},
                )
            }
        }

        compose.onNodeWithText("Separation field test").assertIsDisplayed()
        compose.onAllNodesWithText("member-a").onFirst().performClick()
        compose.onNodeWithText("START FIELD TEST").performClick()

        assertEquals("member-a", startedMember)
    }

    @Test
    fun activeFieldTestDisplaysMilestoneProgressAndCopyableSummary() {
        val state = activeState().copy(
            members = listOf(memberRow("member-a")),
            separationFieldTest = SeparationFieldTestState(
                memberId = "member-a",
                startedAtEpochMillis = 1_000L,
                withinRangeAtEpochMillis = 2_000L,
                watchingAtEpochMillis = 4_000L,
                warningAtEpochMillis = 34_000L,
            ),
        )

        compose.setContent {
            MaterialTheme {
                TrackingProofScreen(
                    state = state,
                    onSetupChanged = {},
                    onStart = {},
                    onStop = {},
                    onRequestPermissions = {},
                    onCopyDiagnostics = {},
                    onStartSeparationFieldTest = {},
                    onResetSeparationFieldTest = {},
                )
            }
        }

        compose.onNodeWithText("Testing member-a").assertIsDisplayed()
        compose.onNodeWithText("Within range: observed").assertIsDisplayed()
        compose.onNodeWithText("Watching: observed").assertIsDisplayed()
        compose.onNodeWithText("WARNING: observed").assertIsDisplayed()
        compose.onNodeWithText("SERIOUS: pending").assertIsDisplayed()
        compose.onNodeWithText("Recovered: pending").assertIsDisplayed()
        compose.onNodeWithText("COPY FIELD TEST RESULT").assertIsDisplayed()
    }

    private fun activeState(): TrackingProofUiState {
        val setup = ProofSetup("impact-proof", "Impact Team Tracking Proof", "blue", "leader", "device-leader")
        val session = ActiveTripSession(
            tripId = TripId("impact-proof"),
            teamId = TeamId("blue"),
            memberId = MemberId("leader"),
            deviceId = DeviceId("device-leader"),
            tripName = "Impact Team Tracking Proof",
            activatedAtEpochMillis = 1_000L,
        )
        return TrackingProofUiState(setup = setup, health = TrackingHealth.Active(session, null))
    }

    private fun memberRow(memberId: String) = MemberStateRowModel(
        memberId = memberId,
        ageLabel = "1 sec ago",
        freshnessLabel = "Current",
        batteryLabel = "90% battery",
        accuracyLabel = "±5 m",
        arrivalLabel = "Direct peer",
        isLastKnown = false,
    )
}
