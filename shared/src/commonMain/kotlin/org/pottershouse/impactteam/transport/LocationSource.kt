package org.pottershouse.impactteam.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.pottershouse.impactteam.domain.GeoPoint
import org.pottershouse.impactteam.protocol.LocationPayload

data class LocationSample(
    val point: GeoPoint,
    val capturedAtEpochMillis: Long,
    val horizontalAccuracyMeters: Double,
    val batteryPercent: Int,
)

fun LocationSample.toLocationPayload(): LocationPayload = LocationPayload(
    latitude = point.latitude,
    longitude = point.longitude,
    capturedAtEpochMillis = capturedAtEpochMillis,
    accuracyMeters = horizontalAccuracyMeters,
    batteryPercent = batteryPercent,
)

data class LocationRequestPolicy(
    val intervalMillis: Long,
)

sealed interface LocationAvailability {
    data object Available : LocationAvailability

    data class Unavailable(val reason: String) : LocationAvailability
}

interface LocationSource {
    val samples: Flow<LocationSample>
    val availability: StateFlow<LocationAvailability>

    suspend fun start(policy: LocationRequestPolicy)

    suspend fun stop()
}
