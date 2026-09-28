package org.pottershouse.impactteam.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TrackingIssue
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.LocationPayload
import org.pottershouse.impactteam.protocol.MessagePriority
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.Freshness
import org.pottershouse.impactteam.state.MemberTrackingState
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.transport.PeerId
import org.pottershouse.impactteam.transport.TransportEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrackingProofViewModelTest {
    @Test
    fun startsIdleAndRequiresEveryMissingPermissionBeforeActivation() = runTest {
        val started = mutableListOf<ActiveTripSession>()
        val viewModel = viewModel(
            missingPermissions = {
                setOf(TrackingPermission.PRECISE_LOCATION, TrackingPermission.BLUETOOTH)
            },
            started = started,
        )
        runCurrent()

        assertIs<TrackingHealth.Idle>(viewModel.state.value.health)
        viewModel.start(setup)

        val required = assertIs<TrackingHealth.PermissionRequired>(viewModel.state.value.health)
        assertEquals(
            setOf(TrackingPermission.PRECISE_LOCATION, TrackingPermission.BLUETOOTH),
            required.permissions,
        )
        assertTrue(started.isEmpty())
    }

    @Test
    fun activeDegradedAndStoppedStatesRemainExplicit() = runTest {
        val telemetry = MutableStateFlow(TrackingProofTelemetrySnapshot())
        var stops = 0
        val viewModel = viewModel(telemetry = telemetry, stopped = { stops += 1 })
        runCurrent()

        viewModel.start(setup)
        runCurrent()
        assertIs<TrackingHealth.Active>(viewModel.state.value.health)

        telemetry.value = telemetry.value.copy(
            health = TrackingHealth.Degraded(
                session,
                setOf(TrackingIssue.LocationServicesDisabled),
            ),
        )
        runCurrent()
        assertIs<TrackingHealth.Degraded>(viewModel.state.value.health)

        viewModel.stop()
        assertEquals(1, stops)
        assertIs<TrackingHealth.Stopped>(viewModel.state.value.health)
    }

    @Test
    fun memberRowsAreSortedAndNeverDescribeStaleDataAsLive() = runTest {
        val members = listOf(
            memberState("zara", Freshness.STALE, ArrivalPath.RELAYED, relayCount = 3),
            memberState("amy", Freshness.CURRENT, ArrivalPath.LOCAL),
            memberState("ben", Freshness.BECOMING_STALE, ArrivalPath.DIRECT_PEER),
        )
        val viewModel = viewModel(loadMembers = { members })
        runCurrent()

        viewModel.start(setup)
        runCurrent()

        assertEquals(listOf("amy", "ben", "zara"), viewModel.state.value.members.map { it.memberId })
        assertEquals("Local", viewModel.state.value.members[0].arrivalLabel)
        assertEquals("Direct peer", viewModel.state.value.members[1].arrivalLabel)
        assertEquals("Relayed (3 hops)", viewModel.state.value.members[2].arrivalLabel)
        assertEquals("Last known", viewModel.state.value.members[2].freshnessLabel)
        assertFalse(viewModel.state.value.members[2].freshnessLabel.contains("live", ignoreCase = true))
    }

    @Test
    fun diagnosticsAreRedactedAndExposeOperationalCounters() = runTest {
        val telemetry = MutableStateFlow(
            TrackingProofTelemetrySnapshot(
                connectedPeers = setOf(PeerId(DeviceId("peer-a")), PeerId(DeviceId("peer-b"))),
                lastExchangeAtEpochMillis = now - 12_000,
                acceptedCount = 7,
                duplicateCount = 2,
                rejectedCount = 1,
                transportFailures = listOf("Bluetooth unavailable"),
                batteryPercent = 41,
                authenticationDigits = mapOf(PeerId(DeviceId("peer-a")) to "4821"),
            ),
        )
        val viewModel = viewModel(telemetry = telemetry)
        runCurrent()

        val diagnostics = viewModel.state.value.diagnostics
        assertEquals(2, diagnostics.nearbyCount)
        assertEquals("12 sec ago", diagnostics.lastExchangeAge)
        assertEquals(7, diagnostics.acceptedCount)
        assertEquals("4821", diagnostics.authenticationDigits.single().digits)
        assertFalse(diagnostics.redactedText().contains("latitude", ignoreCase = true))
        assertFalse(diagnostics.redactedText().contains("longitude", ignoreCase = true))
    }

    @Test
    fun telemetryStoreCountsRecordsAndTracksPeerLifecycle() {
        val store = TrackingProofTelemetryStore()
        val peer = PeerId(DeviceId("peer-a"))
        val observed = memberState("amy", Freshness.CURRENT, ArrivalPath.LOCAL).observed

        store.recordTransport(TransportEvent.Connected(peer), now)
        store.recordTransport(TransportEvent.AuthenticationRequired(peer, "4821"), now)
        store.recordAccept(AcceptResult.Accepted(observed, replacedPrevious = false))
        store.recordAccept(AcceptResult.Duplicate(observed.envelope.recordId))
        store.recordTransport(TransportEvent.Failure(peer, "radio stopped"), now)

        assertEquals(setOf(peer), store.state.value.connectedPeers)
        assertEquals(1, store.state.value.acceptedCount)
        assertEquals(1, store.state.value.duplicateCount)
        assertEquals(41, store.state.value.batteryPercent)
        assertEquals(listOf("radio stopped"), store.state.value.transportFailures)

        store.recordTransport(TransportEvent.Disconnected(peer), now + 1)
        assertTrue(store.state.value.connectedPeers.isEmpty())
    }

    private fun TestScope.viewModel(
        missingPermissions: () -> Set<TrackingPermission> = { emptySet() },
        started: MutableList<ActiveTripSession> = mutableListOf(),
        stopped: () -> Unit = {},
        loadMembers: suspend (TripId) -> List<MemberTrackingState> = { emptyList() },
        telemetry: MutableStateFlow<TrackingProofTelemetrySnapshot> = MutableStateFlow(
            TrackingProofTelemetrySnapshot(),
        ),
    ) = TrackingProofViewModel(
        scope = backgroundScope,
        nowEpochMillis = { now },
        missingPermissions = missingPermissions,
        startTracking = { started += it },
        stopTracking = stopped,
        loadMembers = loadMembers,
        telemetry = telemetry,
    )

    private fun memberState(
        memberId: String,
        freshness: Freshness,
        arrivalPath: ArrivalPath,
        relayCount: Int = 0,
    ): MemberTrackingState {
        val envelope = TrackingEnvelope(
            protocolVersion = 1,
            recordId = RecordId("record-$memberId"),
            tripId = TripId("trip-zim-2027"),
            teamId = TeamId("blue"),
            memberId = MemberId(memberId),
            originDeviceId = DeviceId("device-$memberId"),
            originSequence = 1,
            createdAtEpochMillis = now - 12_000,
            priority = MessagePriority.NORMAL,
            expiresAtEpochMillis = now + 60_000,
            payload = LocationPayload(
                latitude = -17.8252,
                longitude = 31.0335,
                capturedAtEpochMillis = now - 12_000,
                accuracyMeters = 8.4,
                batteryPercent = 41,
            ),
        )
        return MemberTrackingState(
            memberId = envelope.memberId,
            observed = ObservedEnvelope(
                envelope = envelope,
                receivedAtEpochMillis = now - 12_000,
                arrivalPath = arrivalPath,
                suppliedByPeerId = null,
                relayCount = relayCount,
            ),
            freshness = freshness,
        )
    }

    private companion object {
        const val now = 1_800_000_000_000L
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
            activatedAtEpochMillis = now,
        )
    }
}
