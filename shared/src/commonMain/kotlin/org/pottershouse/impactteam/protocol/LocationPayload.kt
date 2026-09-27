package org.pottershouse.impactteam.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationPayload(
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    @SerialName("captured_at_epoch_millis")
    val capturedAtEpochMillis: Long,
    @SerialName("accuracy_meters")
    val accuracyMeters: Double,
    @SerialName("battery_percent")
    val batteryPercent: Int,
)
