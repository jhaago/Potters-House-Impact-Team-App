package org.pottershouse.impactteam.transport

sealed interface TransportEvent {
    data class Discovered(val peerId: PeerId) : TransportEvent
    data class AuthenticationRequired(val peerId: PeerId, val token: String) : TransportEvent
    data class Connected(val peerId: PeerId) : TransportEvent
    data class Disconnected(val peerId: PeerId) : TransportEvent
    data class MessageReceived(val peerId: PeerId, val message: PeerMessage) : TransportEvent
    data class Failure(val peerId: PeerId?, val reason: String) : TransportEvent
}
