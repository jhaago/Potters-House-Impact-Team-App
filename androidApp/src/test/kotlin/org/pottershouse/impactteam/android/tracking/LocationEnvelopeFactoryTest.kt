package org.pottershouse.impactteam.android.tracking

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.GeoPoint
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.transport.LocationSample

class LocationEnvelopeFactoryTest {
    @Test
    fun lowAccuracySampleRetainsItsReportedAccuracy() = runBlocking {
        val factory = LocationEnvelopeFactory(
            nextSequence = { 42L },
            nowEpochMillis = { 2_000L },
        )

        val observed = factory.create(
            session = session,
            sample = LocationSample(
                point = GeoPoint(-17.8252, 31.0335),
                capturedAtEpochMillis = 1_900L,
                horizontalAccuracyMeters = 420.0,
                batteryPercent = 37,
            ),
        )

        assertEquals(420.0, observed.envelope.payload.accuracyMeters, 0.0)
        assertEquals(42L, observed.envelope.originSequence)
        assertEquals("device-android:42", observed.envelope.recordId.value)
        assertEquals(ArrivalPath.LOCAL, observed.arrivalPath)
        assertEquals(2_000L, observed.receivedAtEpochMillis)
        assertEquals(86_402_000L, observed.envelope.expiresAtEpochMillis)
        assertNull(observed.suppliedByPeerId)
    }

    private companion object {
        val session = ActiveTripSession(
            tripId = TripId("trip-zimbabwe-2027"),
            teamId = TeamId("team-blue"),
            memberId = MemberId("member-jordan"),
            deviceId = DeviceId("device-android"),
            tripName = "Zimbabwe Impact Team 2027",
            activatedAtEpochMillis = 900L,
        )
    }
}
