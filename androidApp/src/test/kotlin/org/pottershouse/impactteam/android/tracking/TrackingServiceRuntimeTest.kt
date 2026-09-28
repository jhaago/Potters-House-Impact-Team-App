package org.pottershouse.impactteam.android.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.state.AcceptResult
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
            nowEpochMillis = { 1_700_000_100_000L },
        )

    private class FakePeerTransport(private val calls: MutableList<String>) : PeerTransport {
        override val events: Flow<TransportEvent> = MutableSharedFlow()

        override suspend fun start(session: ActiveTripSession) {
            calls += "peer:start"
        }

        override suspend fun send(peerId: PeerId, message: PeerMessage) = Unit

        override suspend fun stop() {
            calls += "peer:stop"
        }
    }

    private companion object {
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
