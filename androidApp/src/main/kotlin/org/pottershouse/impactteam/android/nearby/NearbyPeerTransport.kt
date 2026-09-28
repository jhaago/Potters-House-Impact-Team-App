package org.pottershouse.impactteam.android.nearby

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.transport.PeerId
import org.pottershouse.impactteam.transport.PeerMessage
import org.pottershouse.impactteam.transport.PeerMessageCodec
import org.pottershouse.impactteam.transport.PeerMessageDecodeResult
import org.pottershouse.impactteam.transport.PeerTransport
import org.pottershouse.impactteam.transport.TransportEvent

class NearbyPeerTransport(
    private val client: NearbyClient,
    private val scope: CoroutineScope,
) : PeerTransport {
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    override val events: Flow<TransportEvent> = mutableEvents.asSharedFlow()

    private val peerByEndpoint = mutableMapOf<String, PeerId>()
    private val endpointByPeer = mutableMapOf<PeerId, String>()
    private var localEndpointName: String? = null
    private var clientJob: Job? = null
    private var active = false

    override suspend fun start(session: ActiveTripSession) {
        if (active) return
        active = true
        localEndpointName = session.deviceId.value
        clientJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.collect(::handleClientEvent)
        }
        val config = NearbyStartConfig(
            endpointName = session.deviceId.value,
            serviceId = SERVICE_ID,
            strategy = NearbyStrategy.P2P_CLUSTER,
        )
        client.startAdvertising(config)
        client.startDiscovery(config)
    }

    override suspend fun send(peerId: PeerId, message: PeerMessage) {
        val endpointId = endpointByPeer[peerId]
        if (endpointId == null) {
            mutableEvents.emit(TransportEvent.Failure(peerId, "Peer is not connected"))
            return
        }
        client.sendBytes(endpointId, PeerMessageCodec.encode(message))
    }

    override suspend fun stop() {
        if (!active) return
        active = false
        clientJob?.cancelAndJoin()
        clientJob = null
        client.stopDiscovery()
        client.stopAdvertising()
        client.stopAllEndpoints()
        peerByEndpoint.clear()
        endpointByPeer.clear()
        localEndpointName = null
    }

    private suspend fun handleClientEvent(event: NearbyClientEvent) {
        when (event) {
            is NearbyClientEvent.EndpointFound -> {
                val peerId = rememberPeer(event.endpointId, event.endpointName)
                mutableEvents.emit(TransportEvent.Discovered(peerId))
                client.requestConnection(localEndpointName.orEmpty(), event.endpointId)
            }
            is NearbyClientEvent.EndpointLost -> forgetPeer(event.endpointId)
            is NearbyClientEvent.ConnectionInitiated -> {
                val peerId = rememberPeer(event.endpointId, event.endpointName)
                mutableEvents.emit(
                    TransportEvent.AuthenticationRequired(peerId, event.authenticationToken),
                )
                client.acceptConnection(event.endpointId)
            }
            is NearbyClientEvent.Connected -> peerByEndpoint[event.endpointId]?.let { peerId ->
                mutableEvents.emit(TransportEvent.Connected(peerId))
            } ?: mutableEvents.emit(
                TransportEvent.Failure(null, "Connected endpoint has no peer identity"),
            )
            is NearbyClientEvent.Disconnected -> forgetPeer(event.endpointId)?.let { peerId ->
                mutableEvents.emit(TransportEvent.Disconnected(peerId))
            }
            is NearbyClientEvent.BytesReceived -> receiveBytes(event)
            is NearbyClientEvent.Failure -> mutableEvents.emit(
                TransportEvent.Failure(event.endpointId?.let(peerByEndpoint::get), event.reason),
            )
        }
    }

    private suspend fun receiveBytes(event: NearbyClientEvent.BytesReceived) {
        val peerId = peerByEndpoint[event.endpointId]
        if (peerId == null) {
            mutableEvents.emit(TransportEvent.Failure(null, "Payload came from an unknown endpoint"))
            return
        }
        when (val decoded = PeerMessageCodec.decode(event.bytes)) {
            is PeerMessageDecodeResult.Success -> mutableEvents.emit(
                TransportEvent.MessageReceived(peerId, decoded.message),
            )
            is PeerMessageDecodeResult.Invalid -> mutableEvents.emit(
                TransportEvent.Failure(peerId, decoded.reason),
            )
            is PeerMessageDecodeResult.TooLarge -> mutableEvents.emit(
                TransportEvent.Failure(peerId, "Payload is too large: ${decoded.encodedBytes} bytes"),
            )
            is PeerMessageDecodeResult.UnsupportedProtocol -> mutableEvents.emit(
                TransportEvent.Failure(peerId, "Unsupported protocol ${decoded.protocolVersion}"),
            )
        }
    }

    private fun rememberPeer(endpointId: String, endpointName: String): PeerId {
        val peerId = PeerId(DeviceId(endpointName))
        peerByEndpoint[endpointId] = peerId
        endpointByPeer[peerId] = endpointId
        return peerId
    }

    private fun forgetPeer(endpointId: String): PeerId? {
        val peerId = peerByEndpoint.remove(endpointId) ?: return null
        endpointByPeer.remove(peerId)
        return peerId
    }

    companion object {
        const val SERVICE_ID = "org.pottershouse.impactteam.nearby.v1"
    }
}
