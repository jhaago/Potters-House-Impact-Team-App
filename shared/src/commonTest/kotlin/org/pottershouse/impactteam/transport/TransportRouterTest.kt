package org.pottershouse.impactteam.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.locationEnvelope
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.sync.SyncBatch
import org.pottershouse.impactteam.sync.SyncDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransportRouterTest {
    private val now = 1_700_000_100_000L

    @Test
    fun startAndStopAreIdempotent() = runTest {
        val transport = FakePeerTransport()
        val router = router(transport)

        assertTrue(router.start(session))
        assertTrue(!router.start(session))
        assertTrue(router.stop())
        assertTrue(!router.stop())

        assertEquals(1, transport.startCount)
        assertEquals(1, transport.stopCount)
    }

    @Test
    fun connectedPeerReceivesDigestBeforeAnyBatch() = runTest {
        val transport = FakePeerTransport()
        val router = router(transport)
        router.start(session)

        transport.emit(TransportEvent.Connected(peer))
        runCurrent()

        val sent = transport.sent.single()
        assertEquals(peer, sent.first)
        assertIs<PeerMessage.Digest>(sent.second)
    }

    @Test
    fun validIncomingBatchReachesRepositoryAsPeerObservation() = runTest {
        val transport = FakePeerTransport()
        val accepted = mutableListOf<ObservedEnvelope>()
        val router = router(transport, accepted)
        router.start(session)
        val envelope = locationEnvelope().copy(
            recordId = RecordId("record-peer-1"),
            tripId = session.tripId,
            originDeviceId = DeviceId("device-peer"),
            originSequence = 4,
            expiresAtEpochMillis = now + 60_000,
        )

        transport.emit(
            TransportEvent.MessageReceived(
                peer,
                PeerMessage.Batch(listOf(PeerRecord(envelope, relayCount = 1))),
            ),
        )
        runCurrent()

        val observed = accepted.single()
        assertEquals(envelope, observed.envelope)
        assertEquals(now, observed.receivedAtEpochMillis)
        assertEquals(peer.deviceId, observed.suppliedByPeerId)
        assertEquals(1, observed.relayCount)
    }

    @Test
    fun invalidBatchDoesNotTerminateLaterExchange() = runTest {
        val transport = FakePeerTransport()
        val accepted = mutableListOf<ObservedEnvelope>()
        val router = router(transport, accepted)
        router.start(session)
        val expired = locationEnvelope().copy(
            recordId = RecordId("expired"),
            tripId = session.tripId,
            expiresAtEpochMillis = now,
        )
        val wrongTrip = locationEnvelope().copy(
            recordId = RecordId("wrong-trip"),
            tripId = TripId("another-trip"),
            expiresAtEpochMillis = now + 60_000,
        )
        val valid = locationEnvelope().copy(
            recordId = RecordId("valid"),
            tripId = session.tripId,
            originSequence = 8,
            expiresAtEpochMillis = now + 60_000,
        )

        transport.emit(TransportEvent.MessageReceived(peer, PeerMessage.Batch(listOf(PeerRecord(expired)))))
        transport.emit(TransportEvent.MessageReceived(peer, PeerMessage.Batch(listOf(PeerRecord(wrongTrip)))))
        transport.emit(TransportEvent.MessageReceived(peer, PeerMessage.Batch(listOf(PeerRecord(valid)))))
        runCurrent()

        assertEquals(listOf(RecordId("valid")), accepted.map { it.envelope.recordId })
    }

    @Test
    fun disconnectedPeerCanReconnectAndReceiveFreshDigest() = runTest {
        val transport = FakePeerTransport()
        var digestSequence = 1L
        val router = router(transport, digest = {
            SyncDigest(mapOf(DeviceId("local") to digestSequence++), emptySet())
        })
        router.start(session)

        transport.emit(TransportEvent.Connected(peer))
        transport.emit(TransportEvent.Disconnected(peer))
        transport.emit(TransportEvent.Connected(peer))
        runCurrent()

        val sequences = transport.sent.map { sent ->
            assertIs<PeerMessage.Digest>(sent.second).digest.highestSequenceByOrigin.values.single()
        }
        assertEquals(listOf(1L, 2L), sequences)
    }

    private fun TestScope.router(
        transport: FakePeerTransport,
        accepted: MutableList<ObservedEnvelope> = mutableListOf(),
        digest: () -> SyncDigest = { SyncDigest.EMPTY },
    ) = TransportRouter(
        transport = transport,
        scope = backgroundScope,
        nowEpochMillis = { now },
        digestProvider = digest,
        batchPlanner = { _, _ -> SyncBatch(emptyList(), 0) },
        acceptRecord = {
            accepted += it
            AcceptResult.Accepted(it, replacedPrevious = false)
        },
    )

    private class FakePeerTransport : PeerTransport {
        private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)
        override val events: Flow<TransportEvent> = mutableEvents
        val sent = mutableListOf<Pair<PeerId, PeerMessage>>()
        var startCount = 0
        var stopCount = 0

        override suspend fun start(session: ActiveTripSession) {
            startCount += 1
        }

        override suspend fun send(peerId: PeerId, message: PeerMessage) {
            sent += peerId to message
        }

        override suspend fun stop() {
            stopCount += 1
        }

        fun emit(event: TransportEvent) {
            check(mutableEvents.tryEmit(event))
        }
    }

    private companion object {
        val peer = PeerId(DeviceId("peer-device"))
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
