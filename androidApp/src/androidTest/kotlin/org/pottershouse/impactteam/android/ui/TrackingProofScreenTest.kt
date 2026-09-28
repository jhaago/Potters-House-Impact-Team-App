package org.pottershouse.impactteam.android.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TripId

class TrackingProofScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startRequiresExplicitConfirmation() {
        var started = false
        compose.setContent {
            TrackingProofScreen(
                state = idleState,
                onSetupChanged = {},
                onStart = { started = true },
                onStop = {},
                onRequestPermissions = {},
                onCopyDiagnostics = {},
            )
        }

        compose.onNodeWithText("START TRACKING").performClick()
        compose.onNodeWithText("Start tracking for this trip?").assertIsDisplayed()
        assertFalse(started)
        compose.onNodeWithText("CONFIRM START").performClick()
        assertTrue(started)
    }

    @Test
    fun activeTrackingIsProminentAndStopIsAlwaysAvailable() {
        compose.setContent {
            TrackingProofScreen(
                state = idleState.copy(health = TrackingHealth.Active(session, null)),
                onSetupChanged = {},
                onStart = {},
                onStop = {},
                onRequestPermissions = {},
                onCopyDiagnostics = {},
            )
        }

        compose.onNodeWithText("Tracking status: Active").assertIsDisplayed()
        compose.onNodeWithText("STOP TRACKING").assertIsDisplayed()
    }

    @Test
    fun staleMemberIsMarkedLastKnown() {
        compose.setContent {
            TrackingProofScreen(
                state = idleState.copy(
                    health = TrackingHealth.Active(session, null),
                    members = listOf(
                        MemberStateRowModel(
                            memberId = "david",
                            ageLabel = "3 min ago",
                            freshnessLabel = "Last known",
                            batteryLabel = "11% battery",
                            accuracyLabel = "±18 m",
                            arrivalLabel = "Relayed (2 hops)",
                            isLastKnown = true,
                        ),
                    ),
                ),
                onSetupChanged = {},
                onStart = {},
                onStop = {},
                onRequestPermissions = {},
                onCopyDiagnostics = {},
            )
        }

        compose.onNodeWithText("Last known").assertIsDisplayed()
        compose.onNodeWithText("Live").assertDoesNotExist()
    }

    @Test
    fun diagnosticsNeverDisplayCoordinatesByDefault() {
        compose.setContent {
            TrackingProofScreen(
                state = idleState.copy(
                    diagnostics = DiagnosticsUiState(
                        nearbyCount = 2,
                        lastExchangeAge = "12 sec ago",
                        acceptedCount = 7,
                        duplicateCount = 2,
                        rejectedCount = 1,
                        failures = listOf("Bluetooth unavailable"),
                        batteryPercent = 41,
                        authenticationDigits = emptyList(),
                    ),
                ),
                onSetupChanged = {},
                onStart = {},
                onStop = {},
                onRequestPermissions = {},
                onCopyDiagnostics = {},
            )
        }

        compose.onNodeWithText("DIAGNOSTICS").performClick()
        compose.onNodeWithText("Nearby phones: 2").assertIsDisplayed()
        compose.onNodeWithText("-17.8252").assertDoesNotExist()
        compose.onNodeWithText("31.0335").assertDoesNotExist()
    }

    private companion object {
        val setup = ProofSetup(
            tripId = "trip-zim-2027",
            tripName = "Zimbabwe Impact Team 2027",
            teamId = "blue",
            memberId = "amy",
            deviceId = "device-amy",
        )
        val session = ActiveTripSession(
            tripId = TripId(setup.tripId),
            teamId = TeamId(setup.teamId),
            memberId = MemberId(setup.memberId),
            deviceId = DeviceId(setup.deviceId),
            tripName = setup.tripName,
            activatedAtEpochMillis = 1_800_000_000_000L,
        )
        val idleState = TrackingProofUiState(setup = setup)
    }
}
