package org.pottershouse.impactteam.domain

enum class TrackingPermission {
    PRECISE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
    BLUETOOTH,
    LOCAL_NETWORK,
}

sealed interface TrackingIssue {
    data class PermissionRevoked(val permission: TrackingPermission) : TrackingIssue

    data object LocationServicesDisabled : TrackingIssue

    data object BatteryRestricted : TrackingIssue

    data class LocationUnavailable(val reason: String) : TrackingIssue
}

sealed interface TrackingHealth {
    data object Idle : TrackingHealth

    data class PermissionRequired(val permissions: Set<TrackingPermission>) : TrackingHealth

    data class Active(
        val session: ActiveTripSession,
        val lastLocationAtEpochMillis: Long?,
    ) : TrackingHealth

    data class Degraded(
        val session: ActiveTripSession,
        val issues: Set<TrackingIssue>,
    ) : TrackingHealth

    data class Stopped(val reason: String) : TrackingHealth

    companion object {
        fun permissionRevoked(
            session: ActiveTripSession,
            permission: TrackingPermission,
        ): Degraded = Degraded(
            session = session,
            issues = setOf(TrackingIssue.PermissionRevoked(permission)),
        )
    }
}
