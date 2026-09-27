package org.pottershouse.impactteam.protocol

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.TripId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EnvelopeValidatorTest {
    private val now = 1_700_000_100_000L

    @Test
    fun validV1RecordIsAccepted() {
        assertEquals(ValidationResult.Valid, EnvelopeValidator.validate(locationEnvelope(), now))
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertInvalid(locationEnvelope().withPayload(latitude = 90.1), ValidationFailure.LatitudeOutOfRange)
        assertInvalid(locationEnvelope().withPayload(longitude = -180.1), ValidationFailure.LongitudeOutOfRange)
    }

    @Test
    fun nonPositiveSequenceIsRejected() {
        assertInvalid(locationEnvelope().copy(originSequence = 0), ValidationFailure.NonPositiveSequence)
    }

    @Test
    fun blankIdentifiersAreRejected() {
        assertInvalid(locationEnvelope().copy(tripId = TripId("  ")), ValidationFailure.BlankIdentifier)
        assertInvalid(locationEnvelope().copy(originDeviceId = DeviceId("")), ValidationFailure.BlankIdentifier)
    }

    @Test
    fun invalidAccuracyAndBatteryAreRejected() {
        assertInvalid(locationEnvelope().withPayload(accuracyMeters = -0.1), ValidationFailure.NegativeAccuracy)
        assertInvalid(locationEnvelope().withPayload(batteryPercent = -1), ValidationFailure.BatteryOutOfRange)
        assertInvalid(locationEnvelope().withPayload(batteryPercent = 101), ValidationFailure.BatteryOutOfRange)
    }

    @Test
    fun expiredEnvelopeIsRejected() {
        assertInvalid(locationEnvelope().copy(expiresAtEpochMillis = now), ValidationFailure.Expired)
    }

    private fun assertInvalid(envelope: TrackingEnvelope, expected: ValidationFailure) {
        val result = assertIs<ValidationResult.Invalid>(EnvelopeValidator.validate(envelope, now))
        assertTrue(expected in result.failures)
    }
}

private fun TrackingEnvelope.withPayload(
    latitude: Double = payload.latitude,
    longitude: Double = payload.longitude,
    accuracyMeters: Double = payload.accuracyMeters,
    batteryPercent: Int = payload.batteryPercent,
) = copy(
    payload = payload.copy(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        batteryPercent = batteryPercent,
    ),
)
