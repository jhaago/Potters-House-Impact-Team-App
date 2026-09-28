package org.pottershouse.impactteam.domain

data class ActiveTripSession(
    val tripId: TripId,
    val teamId: TeamId,
    val memberId: MemberId,
    val deviceId: DeviceId,
    val tripName: String,
    val activatedAtEpochMillis: Long,
)
