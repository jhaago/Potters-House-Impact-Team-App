package org.pottershouse.impactteam.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import org.pottershouse.impactteam.domain.GeoPoint

class LocationSourceContractTest {
    @Test
    fun poorGpsAccuracyIsPreservedWhenCreatingPayload() {
        val sample = LocationSample(
            point = GeoPoint(latitude = -17.8252, longitude = 31.0335),
            capturedAtEpochMillis = 1_798_000_000_000,
            horizontalAccuracyMeters = 420.0,
            batteryPercent = 37,
        )

        val payload = sample.toLocationPayload()

        assertEquals(420.0, payload.accuracyMeters)
        assertEquals(-17.8252, payload.latitude)
        assertEquals(31.0335, payload.longitude)
        assertEquals(1_798_000_000_000, payload.capturedAtEpochMillis)
        assertEquals(37, payload.batteryPercent)
    }
}
