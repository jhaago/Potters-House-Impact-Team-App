package org.pottershouse.impactteam.android.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.locationEnvelope
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.state.TrackingLedger
import org.pottershouse.impactteam.transport.PeerId
import org.pottershouse.impactteam.transport.PeerMessage
import org.pottershouse.impactteam.transport.PeerTransport
import org.pottershouse.impactteam.transport.TransportEvent

class TrackingServiceRuntimeTest {
    @Test
    fun repeatedStartForSameTripIsIdempotent() = runTest {
        val calls = mutableListOf<String>()
        val runtime = runtime(calls)

        runtime.start(session)
        runtime.start(session)

        assertEquals(1, calls.count { it == "location:start" })
        assertEquals(1, calls.count { it == "peer:start" })
    }

    @Test
    fun activeTripStartsAndStopsLocationAndPeerExchangeTogether() = runTest {
        val calls = mutableListOf<String>()
        val runtime = runtime(calls)

        runtime.start(session)
        runtime.stop("Trip ended")

        assertEquals(
            listOf(
                "load:trip-zimbabwe-2027",
                "location:start",
                "peer:start",
                "peer:stop",
                "location:stop:Trip ended",
            ),
            calls,
        )
    }

    @Test
    fun connectedPeerDigestReloadsCurrentLedgerInsteadOfUsingStartupSnapshot() = runTest {
        val calls = mutableListOf<String>()
        val transport = FakePeerTransport(calls)
        var loadCount = 0
        val runtime = TrackingServiceRuntime(
            loadLedger = {
                loadCount += 1
                ledgerWithSequence(loadCount.toLong())
            },
            acceptRecord = { AcceptResult.Accepted(it, replacedPrevious = false) },
            startLocation = { calls += "location:start" },
            stopLocation = { reason -> calls += "location:stop:$reason" },
            peerTransport = transport,
            scope = backgroundScope,
            nowEpochMillis = { NOW },
        )

        runtime.start(session)
        transport.emit(TransportEvent.Connected(peer))
        runCurrent()

        assertEquals(2, loadCount)
        val digest = transport.sent
            .map { it.second }
            .filterIsInstance<PeerMessage.Digest>()
            .single()
            .digest
        assertTrue(digest.highestSequenceByOrigin.values.contains(2L))
    }

    private fun kotlinx.coroutines.test.TestScope.runtime(calls: MutableList<String>) =
        TrackingServiceRuntime(
            loadLedger = { tripId ->
                calls += "load:${tripId.value}"
                TrackingLedger()
            },
            acceptRecord = { AcceptResult.Accepted(it, replacedPrevious = false) },
            startLocation = { calls += "location:start" },
            stopLocation = { reason -> calls += "location:stop:$reason" },
            peerTransport = FakePeerTransport(calls),
            scope = backgroundScope,
            nowEpochMillis = { NOW },
        )

    private fun ledgerWithSequence(sequence: Long): TrackingLedger = TrackingLedger().also { ledger ->
        val envelope = locationEnvelope().copy(
            recordId = RecordId("record-$sequence"),
            tripId = session.tripId,
            teamId = session.teamId,
            memberId = session.memberId,
            originDeviceId = session.deviceId,
            originSequence = sequence,
            createdAtEpochMillis = NOW - 1_000L,
            expiresAtEpochMillis = NOW + 60_000L,
        )
        ledger.restore(
            ObservedEnvelope(
                envelope = envelope,
                receivedAtEpochMillis = NOW - 1_000L,
                arrivalPath = ArrivalPath.LOCAL,
                suppliedByPeerId = null,
            ),
        )
    }

    private class FakePeerTransport(private val calls: MutableList<String>) : PeerTransport {
        private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)
        override val events: Flow<TransportEvent> = mutableEvents
        val sent = mutableListOf<Pair<PeerId, PeerMessage>>()

        override suspend fun start(session: ActiveTripSession) {
            calls += "peer:start"
        }

        override suspend fun send(peerId: PeerId, message: PeerMessage) {
            sent += peerId to message
        }

        override suspend fun stop() {
            calls += "peer:stop"
        }

        fun emit(event: TransportEvent) {
            check(mutableEvents.tryEmit(event))
        }
    }

    private companion object {
        const val NOW = 1_700_000_100_000L
        val peer = PeerId(DeviceId("device-peer"))
        val session = ActiveTripSession(
            tripId = TripId("trip-zimbabwe-2027"),
            teamId = TeamId("team-blue"),
            memberId = MemberId("member-jordan"),
            deviceId = DeviceId("device-local"),
            tripName = "Zimbabwe Impact Team 2027",
            activatedAtEpochMillis = 1_700_000_000_000L,
        )
    }
}
