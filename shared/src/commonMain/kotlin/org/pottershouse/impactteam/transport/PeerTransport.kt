package org.pottershouse.impactteam.transport

import kotlinx.coroutines.flow.Flow
import org.pottershouse.impactteam.domain.ActiveTripSession

interface PeerTransport {
    val events: Flow<TransportEvent>

    suspend fun start(session: ActiveTripSession)
    suspend fun send(peerId: PeerId, message: PeerMessage)
    suspend fun stop()
}
