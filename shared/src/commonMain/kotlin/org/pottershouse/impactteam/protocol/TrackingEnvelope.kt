package org.pottershouse.impactteam.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId

@Serializable
enum class MessagePriority {
    @SerialName("NORMAL")
    NORMAL,

    @SerialName("URGENT")
    URGENT,
}

@Serializable
data class TrackingEnvelope(
    @SerialName("protocol_version")
    val protocolVersion: Int,
    @SerialName("record_id")
    val recordId: RecordId,
    @SerialName("trip_id")
    val tripId: TripId,
    @SerialName("team_id")
    val teamId: TeamId,
    @SerialName("member_id")
    val memberId: MemberId,
    @SerialName("origin_device_id")
    val originDeviceId: DeviceId,
    @SerialName("origin_sequence")
    val originSequence: Long,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("priority")
    val priority: MessagePriority,
    @SerialName("expires_at_epoch_millis")
    val expiresAtEpochMillis: Long,
    @SerialName("payload")
    val payload: LocationPayload,
)
