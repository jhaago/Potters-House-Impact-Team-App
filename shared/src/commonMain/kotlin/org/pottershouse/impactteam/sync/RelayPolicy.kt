package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.state.ObservedEnvelope

class RelayPolicy(
    private val maxRelayCount: Int = 8,
) {
    init {
        require(maxRelayCount > 0)
    }

    fun canOffer(
        observedEnvelope: ObservedEnvelope,
        peerId: DeviceId,
        nowEpochMillis: Long,
    ): Boolean =
        observedEnvelope.envelope.expiresAtEpochMillis > nowEpochMillis &&
            observedEnvelope.relayCount < maxRelayCount &&
            observedEnvelope.suppliedByPeerId != peerId &&
            peerId !in observedEnvelope.recentPeerIds
}
