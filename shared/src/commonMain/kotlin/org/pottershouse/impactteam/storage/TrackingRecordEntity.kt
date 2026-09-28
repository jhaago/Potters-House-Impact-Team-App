package org.pottershouse.impactteam.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.pottershouse.impactteam.state.ObservedEnvelope

@Entity(
    tableName = "tracking_records",
    indices = [
        Index(value = ["trip_id"]),
        Index(value = ["origin_device_id", "origin_sequence"], unique = true),
    ],
)
data class TrackingRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "trip_id")
    val tripId: String,
    @ColumnInfo(name = "origin_device_id")
    val originDeviceId: String,
    @ColumnInfo(name = "origin_sequence")
    val originSequence: Long,
    @ColumnInfo(name = "expires_at_epoch_millis")
    val expiresAtEpochMillis: Long,
    @ColumnInfo(name = "canonical_envelope_json")
    val canonicalEnvelopeJson: String,
    @ColumnInfo(name = "received_at_epoch_millis")
    val receivedAtEpochMillis: Long,
    @ColumnInfo(name = "arrival_path")
    val arrivalPath: String,
    @ColumnInfo(name = "supplied_by_peer_id")
    val suppliedByPeerId: String?,
    @ColumnInfo(name = "relay_count")
    val relayCount: Int,
    @ColumnInfo(name = "recent_peer_ids_json")
    val recentPeerIdsJson: String,
    @ColumnInfo(name = "quarantine_reason")
    val quarantineReason: String? = null,
) {
    companion object {
        private val json = Json

        fun from(observed: ObservedEnvelope, canonicalEnvelopeJson: String) = TrackingRecordEntity(
            recordId = observed.envelope.recordId.value,
            tripId = observed.envelope.tripId.value,
            originDeviceId = observed.envelope.originDeviceId.value,
            originSequence = observed.envelope.originSequence,
            expiresAtEpochMillis = observed.envelope.expiresAtEpochMillis,
            canonicalEnvelopeJson = canonicalEnvelopeJson,
            receivedAtEpochMillis = observed.receivedAtEpochMillis,
            arrivalPath = observed.arrivalPath.name,
            suppliedByPeerId = observed.suppliedByPeerId?.value,
            relayCount = observed.relayCount,
            recentPeerIdsJson = json.encodeToString(observed.recentPeerIds.map { it.value }),
        )
    }
}

@Entity(tableName = "origin_sequences")
data class OriginSequenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "last_sequence")
    val lastSequence: Long,
)

@Entity(tableName = "origin_high_water")
data class OriginHighWaterEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "highest_accepted_sequence")
    val highestAcceptedSequence: Long,
)
