package org.pottershouse.impactteam.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.LocationPayload
import org.pottershouse.impactteam.protocol.MessagePriority
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.Freshness
import org.pottershouse.impactteam.state.MemberTrackingState
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.state.SeparationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackingProofSeparationIntegrationTest {
    @Test
    fun twoPhoneProofClassifiesNearbyPairAsWithinRange() = runTest {
        val members = listOf(
            memberState("amy", -31.95000, 115.86000),
            memberState("sam", -31.94973, 115.86000),
        )
        val viewModel = TrackingProofViewModel(
            scope = backgroundScope,
            nowEpochMillis = { NOW },
            missingPermissions = { emptySet<TrackingPermission>() },
            startTracking = {},
            stopTracking = {},
            loadMembers = { members },
            telemetry = MutableStateFlow(TrackingProofTelemetrySnapshot()),
        )

        viewModel.start(SETUP)
        runCurrent()

        assertEquals(2, viewModel.state.value.members.size)
        viewModel.state.value.members.forEach { member ->
            assertEquals(SeparationLevel.CLEAR, member.separationLevel)
            assertEquals(SeparationLevel.CLEAR, member.rawSeparationLevel)
        }
        assertTrue(
            viewModel.state.value.members.all { member ->
                (member.separationDistanceMeters ?: -1) in 29..31
            },
        )
    }

    @Test
    fun poorAccuracyFixCannotCreateTwoPhoneSeparationAlert() = runTest {
        val members = listOf(
            memberState("amy", -31.95000, 115.86000),
            memberState(
                memberId = "sam",
                latitude = -31.94730,
                longitude = 115.86000,
                accuracyMeters = 150.0,
            ),
        )
        val viewModel = TrackingProofViewModel(
            scope = backgroundScope,
            nowEpochMillis = { NOW },
            missingPermissions = { emptySet<TrackingPermission>() },
            startTracking = {},
            stopTracking = {},
            loadMembers = { members },
            telemetry = MutableStateFlow(TrackingProofTelemetrySnapshot()),
        )

        viewModel.start(SETUP)
        runCurrent()

        assertEquals(
            setOf(SeparationLevel.INSUFFICIENT_DATA),
            viewModel.state.value.members.map { it.rawSeparationLevel }.toSet(),
        )
        assertTrue(viewModel.state.value.members.all { it.separationDistanceMeters == null })
    }

    @Test
    fun rawSeparationIsWatchingBeforeSustainThenPromotesToWarning() = runTest {
        var clock = NOW
        val members = listOf(
            memberState("amy", -17.82520, 31.03350),
            memberState("ben", -17.82500, 31.03365),
            memberState("cara", -17.82535, 31.03375),
            memberState("david", -17.82338, 31.03350),
        )
        val telemetry = MutableStateFlow(TrackingProofTelemetrySnapshot())
        val viewModel = TrackingProofViewModel(
            scope = backgroundScope,
            nowEpochMillis = { clock },
            missingPermissions = { emptySet<TrackingPermission>() },
            startTracking = {},
            stopTracking = {},
            loadMembers = { members },
            telemetry = telemetry,
        )

        viewModel.start(SETUP)
        runCurrent()

        val watching = viewModel.state.value.members.single { it.memberId == "david" }
        assertEquals(SeparationLevel.CLEAR, watching.separationLevel)
        assertEquals(SeparationLevel.WARNING, watching.rawSeparationLevel)

        clock += 31_000
        advanceTimeBy(1_000)
        runCurrent()

        val warning = viewModel.state.value.members.single { it.memberId == "david" }
        assertEquals(SeparationLevel.WARNING, warning.separationLevel)
        assertEquals(SeparationLevel.WARNING, warning.rawSeparationLevel)
    }

    private fun memberState(
        memberId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double = 8.0,
    ): MemberTrackingState {
        val envelope = TrackingEnvelope(
            protocolVersion = 1,
            recordId = RecordId("record-$memberId"),
            tripId = TripId(SETUP.tripId),
            teamId = TeamId(SETUP.teamId),
            memberId = MemberId(memberId),
            originDeviceId = DeviceId("device-$memberId"),
            originSequence = 1,
            createdAtEpochMillis = NOW,
            priority = MessagePriority.NORMAL,
            expiresAtEpochMillis = NOW + 3_600_000,
            payload = LocationPayload(
                latitude = latitude,
                longitude = longitude,
                capturedAtEpochMillis = NOW,
                accuracyMeters = accuracyMeters,
                batteryPercent = 80,
            ),
        )
        return MemberTrackingState(
            memberId = envelope.memberId,
            observed = ObservedEnvelope(
                envelope = envelope,
                receivedAtEpochMillis = NOW,
                arrivalPath = ArrivalPath.DIRECT_PEER,
                suppliedByPeerId = null,
            ),
            freshness = Freshness.CURRENT,
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val SETUP = ProofSetup(
            tripId = "trip-zim-2027",
            tripName = "Zimbabwe Impact Team 2027",
            teamId = "blue",
            memberId = "amy",
            deviceId = "device-amy",
        )
    }
}
