package org.pottershouse.impactteam.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.state.SeparationLevel

class TrackingProofSeparationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun warningMemberShowsDistanceAndMovementStatus() {
        compose.setContent {
            TrackingProofScreen(
                state = TrackingProofUiState(
                    setup = SETUP,
                    health = TrackingHealth.Active(SESSION, null),
                    members = listOf(
                        MemberStateRowModel(
                            memberId = "david",
                            ageLabel = "3 sec ago",
                            freshnessLabel = "Current",
                            batteryLabel = "80% battery",
                            accuracyLabel = "±8 m",
                            arrivalLabel = "Direct peer",
                            isLastKnown = false,
                            separationLevel = SeparationLevel.WARNING,
                            rawSeparationLevel = SeparationLevel.SERIOUS,
                            separationDistanceMeters = 214,
                            separationMovingAway = true,
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

        compose.onNodeWithText("Separation: WARNING • 214 m from group • moving away").assertIsDisplayed()
    }

    @Test
    fun rawWarningBeforeSustainIsShownAsWatchingRatherThanAlert() {
        compose.setContent {
            MemberStateRow(
                MemberStateRowModel(
                    memberId = "david",
                    ageLabel = "1 sec ago",
                    freshnessLabel = "Current",
                    batteryLabel = "80% battery",
                    accuracyLabel = "±8 m",
                    arrivalLabel = "Direct peer",
                    isLastKnown = false,
                    separationLevel = SeparationLevel.CLEAR,
                    rawSeparationLevel = SeparationLevel.WARNING,
                    separationDistanceMeters = 192,
                    separationMovingAway = false,
                ),
            )
        }

        compose.onNodeWithText("Separation: Watching • 192 m from group").assertIsDisplayed()
    }

    private companion object {
        val SETUP = ProofSetup(
            tripId = "trip-zim-2027",
            tripName = "Zimbabwe Impact Team 2027",
            teamId = "blue",
            memberId = "amy",
            deviceId = "device-amy",
        )
        val SESSION = ActiveTripSession(
            tripId = TripId(SETUP.tripId),
            teamId = TeamId(SETUP.teamId),
            memberId = MemberId(SETUP.memberId),
            deviceId = DeviceId(SETUP.deviceId),
            tripName = SETUP.tripName,
            activatedAtEpochMillis = 1_800_000_000_000L,
        )
    }
}
