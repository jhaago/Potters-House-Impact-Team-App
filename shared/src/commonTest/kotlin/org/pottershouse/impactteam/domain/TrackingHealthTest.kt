package org.pottershouse.impactteam.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TrackingHealthTest {
    @Test
    fun revokedLocationPermissionProducesDegradedHealthForActiveTrip() {
        val session = ActiveTripSession(
            tripId = TripId("trip-zimbabwe-2027"),
            teamId = TeamId("team-blue"),
            memberId = MemberId("member-jordan"),
            deviceId = DeviceId("device-iphone"),
            tripName = "Zimbabwe Impact Team 2027",
            activatedAtEpochMillis = 1_798_000_000_000,
        )

        val health = TrackingHealth.permissionRevoked(session, TrackingPermission.BACKGROUND_LOCATION)

        val degraded = assertIs<TrackingHealth.Degraded>(health)
        assertEquals(session, degraded.session)
        assertEquals(setOf(TrackingIssue.PermissionRevoked(TrackingPermission.BACKGROUND_LOCATION)), degraded.issues)
    }
}
