package org.pottershouse.impactteam.android.nearby

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.sync.SyncDigest
import org.pottershouse.impactteam.transport.PeerId
import org.pottershouse.impactteam.transport.PeerMessage
import org.pottershouse.impactteam.transport.PeerMessageCodec
import org.pottershouse.impactteam.transport.TransportEvent

class NearbyPeerTransportTest {
    @Test
    fun startAdvertisesAndDiscoversWithClusterConfiguration() = runTest {
        val client = FakeNearbyClient()
        val transport = NearbyPeerTransport(client, backgroundScope)

        transport.start(session)

        val expected = NearbyStartConfig(
            endpointName = session.deviceId.value,
            serviceId = "org.pottershouse.impactteam.nearby.v1",
            strategy = NearbyStrategy.P2P_CLUSTER,
        )
        assertEquals(listOf(expected), client.advertisingStarts)
        assertEquals(listOf(expected), client.discoveryStarts)
    }

    @Test
    fun connectionRequestExposesAuthenticationTokenAndIsAcceptedForProof() = runTest {
        val client = FakeNearbyClient()
        val transport = NearbyPeerTransport(client, backgroundScope)
        val events = mutableListOf<TransportEvent>()
        backgroundScope.launch { transport.events.toList(events) }
        transport.start(session)

        client.emit(NearbyClientEvent.ConnectionInitiated("endpoint-7", "device-peer", "4821"))
        runCurrent()

        assertEquals(listOf("endpoint-7"), client.acceptedEndpoints)
        assertEquals(
            TransportEvent.AuthenticationRequired(PeerId(DeviceId("device-peer")), "4821"),
            events.single(),
        )
    }

    @Test
    fun discoveryRequestsAConnectionAndConnectedPeerCanReceiveBytes() = runTest {
        val client = FakeNearbyClient()
        val transport = NearbyPeerTransport(client, backgroundScope)
        transport.start(session)
        client.emit(NearbyClientEvent.EndpointFound("endpoint-2", "device-peer"))
        client.emit(NearbyClientEvent.Connected("endpoint-2"))
        runCurrent()

        transport.send(PeerId(DeviceId("device-peer")), PeerMessage.Digest(SyncDigest.EMPTY))

        assertEquals(listOf("endpoint-2"), client.requestedEndpoints)
        assertEquals("endpoint-2", client.sent.single().first)
        assertTrue(client.sent.single().second.size <= 64 * 1024)
    }

    @Test
    fun higherDeviceIdDoesNotAlsoRequestConnectionWhenPairIsDiscovered() = runTest {
        val client = FakeNearbyClient()
        val higherIdSession = session.copy(deviceId = DeviceId("device-zulu"))
        val transport = NearbyPeerTransport(client, backgroundScope)
        transport.start(higherIdSession)

        client.emit(NearbyClientEvent.EndpointFound("endpoint-2", "device-alpha"))
        runCurrent()

        assertTrue(client.requestedEndpoints.isEmpty())
    }

    @Test
    fun malformedPayloadDoesNotPreventNextValidMessage() = runTest {
        val client = FakeNearbyClient()
        val transport = NearbyPeerTransport(client, backgroundScope)
        val events = mutableListOf<TransportEvent>()
        backgroundScope.launch { transport.events.toList(events) }
        transport.start(session)
        client.emit(NearbyClientEvent.EndpointFound("endpoint-2", "device-peer"))
        client.emit(NearbyClientEvent.Connected("endpoint-2"))
        client.emit(NearbyClientEvent.BytesReceived("endpoint-2", "{bad".encodeToByteArray()))
        client.emit(
            NearbyClientEvent.BytesReceived(
                "endpoint-2",
                PeerMessageCodec.encode(PeerMessage.Digest(SyncDigest.EMPTY)),
            ),
        )
        runCurrent()

        assertTrue(events.any { it is TransportEvent.Failure })
        assertTrue(events.any { it is TransportEvent.MessageReceived })
    }

    @Test
    fun stopTerminatesDiscoveryAdvertisingAndEndpoints() = runTest {
        val client = FakeNearbyClient()
        val transport = NearbyPeerTransport(client, backgroundScope)
        transport.start(session)

        transport.stop()
        transport.stop()

        assertEquals(1, client.stopAdvertisingCount)
        assertEquals(1, client.stopDiscoveryCount)
        assertEquals(1, client.stopAllEndpointsCount)
    }

    private class FakeNearbyClient : NearbyClient {
        private val mutableEvents = MutableSharedFlow<NearbyClientEvent>(extraBufferCapacity = 16)
        override val events: Flow<NearbyClientEvent> = mutableEvents
        val advertisingStarts = mutableListOf<NearbyStartConfig>()
        val discoveryStarts = mutableListOf<NearbyStartConfig>()
        val requestedEndpoints = mutableListOf<String>()
        val acceptedEndpoints = mutableListOf<String>()
        val sent = mutableListOf<Pair<String, ByteArray>>()
        var stopAdvertisingCount = 0
        var stopDiscoveryCount = 0
        var stopAllEndpointsCount = 0

        override suspend fun startAdvertising(config: NearbyStartConfig) {
            advertisingStarts += config
        }

        override suspend fun startDiscovery(config: NearbyStartConfig) {
            discoveryStarts += config
        }

        override suspend fun requestConnection(endpointName: String, endpointId: String) {
            requestedEndpoints += endpointId
        }

        override suspend fun acceptConnection(endpointId: String) {
            acceptedEndpoints += endpointId
        }

        override suspend fun sendBytes(endpointId: String, bytes: ByteArray) {
            sent += endpointId to bytes
        }

        override suspend fun stopAdvertising() {
            stopAdvertisingCount += 1
        }

        override suspend fun stopDiscovery() {
            stopDiscoveryCount += 1
        }

        override suspend fun stopAllEndpoints() {
            stopAllEndpointsCount += 1
        }

        fun emit(event: NearbyClientEvent) {
            check(mutableEvents.tryEmit(event))
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
