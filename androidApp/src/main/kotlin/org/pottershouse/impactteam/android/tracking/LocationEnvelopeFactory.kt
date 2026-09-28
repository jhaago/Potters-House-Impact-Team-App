package org.pottershouse.impactteam.android.tracking

import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.EnvelopeCodec
import org.pottershouse.impactteam.protocol.EnvelopeValidator
import org.pottershouse.impactteam.protocol.MessagePriority
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.protocol.ValidationResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.transport.LocationSample
import org.pottershouse.impactteam.transport.toLocationPayload

class LocationEnvelopeFactory(
    private val nextSequence: suspend (DeviceId) -> Long,
    private val nowEpochMillis: () -> Long,
) {
    suspend fun create(
        session: ActiveTripSession,
        sample: LocationSample,
    ): ObservedEnvelope {
        val receivedAt = nowEpochMillis()
        val sequence = nextSequence(session.deviceId)
        val envelope = TrackingEnvelope(
            protocolVersion = EnvelopeCodec.SUPPORTED_PROTOCOL_VERSION,
            recordId = RecordId("${session.deviceId.value}:$sequence"),
            tripId = session.tripId,
            teamId = session.teamId,
            memberId = session.memberId,
            originDeviceId = session.deviceId,
            originSequence = sequence,
            createdAtEpochMillis = sample.capturedAtEpochMillis,
            priority = MessagePriority.NORMAL,
            expiresAtEpochMillis = receivedAt + LOCATION_RECORD_TTL_MILLIS,
            payload = sample.toLocationPayload(),
        )
        check(EnvelopeValidator.validate(envelope, receivedAt) is ValidationResult.Valid) {
            "Platform location sample produced an invalid tracking envelope"
        }
        return ObservedEnvelope(
            envelope = envelope,
            receivedAtEpochMillis = receivedAt,
            arrivalPath = ArrivalPath.LOCAL,
            suppliedByPeerId = null,
        )
    }

    private companion object {
        const val LOCATION_RECORD_TTL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
