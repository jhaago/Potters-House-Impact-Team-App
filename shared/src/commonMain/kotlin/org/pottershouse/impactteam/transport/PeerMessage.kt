package org.pottershouse.impactteam.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.sync.SyncDigest

@Serializable
data class PeerRecord(
    val envelope: TrackingEnvelope,
    @SerialName("relay_count")
    val relayCount: Int = 0,
    @SerialName("recent_peer_ids")
    val recentPeerIds: List<DeviceId> = emptyList(),
)

@Serializable
sealed interface PeerMessage {
    @Serializable
    @SerialName("HELLO")
    data class Hello(@SerialName("device_id") val deviceId: DeviceId) : PeerMessage

    @Serializable
    @SerialName("DIGEST")
    data class Digest(val digest: SyncDigest) : PeerMessage

    @Serializable
    @SerialName("BATCH")
    data class Batch(val records: List<PeerRecord>) : PeerMessage

    @Serializable
    @SerialName("ERROR")
    data class Error(val code: String, val detail: String) : PeerMessage
}
