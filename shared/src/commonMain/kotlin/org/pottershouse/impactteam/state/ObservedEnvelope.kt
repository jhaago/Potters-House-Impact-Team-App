package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.protocol.TrackingEnvelope

data class ObservedEnvelope(
    val envelope: TrackingEnvelope,
    val receivedAtEpochMillis: Long,
    val arrivalPath: ArrivalPath,
    val suppliedByPeerId: DeviceId?,
    val relayCount: Int = 0,
    val recentPeerIds: List<DeviceId> = suppliedByPeerId?.let(::listOf).orEmpty(),
)
