package org.pottershouse.impactteam.android.tracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TrackingIssue
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.transport.LocationAvailability
import org.pottershouse.impactteam.transport.LocationRequestPolicy
import org.pottershouse.impactteam.transport.LocationSource

sealed interface StartResult {
    data object SessionRequired : StartResult
    data object Started : StartResult
    data object AlreadyActive : StartResult
}

class ActiveTripController(
    private val locationSource: LocationSource,
    private val envelopeFactory: LocationEnvelopeFactory,
    private val acceptRecord: suspend (ObservedEnvelope) -> AcceptResult,
    private val scope: CoroutineScope,
) {
    private val mutableHealth = MutableStateFlow<TrackingHealth>(TrackingHealth.Idle)
    val health: StateFlow<TrackingHealth> = mutableHealth.asStateFlow()

    private var activeSession: ActiveTripSession? = null
    private var sampleJob: Job? = null
    private var availabilityJob: Job? = null
    private var lastLocationAtEpochMillis: Long? = null

    suspend fun start(session: ActiveTripSession?): StartResult {
        if (session == null) return StartResult.SessionRequired
        if (activeSession == session && sampleJob?.isActive == true) return StartResult.AlreadyActive
        if (activeSession != null) stop("Active trip changed")

        activeSession = session
        lastLocationAtEpochMillis = null
        mutableHealth.value = TrackingHealth.Active(session, lastLocationAtEpochMillis)
        sampleJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationSource.samples.collect { sample ->
                val observed = envelopeFactory.create(session, sample)
                acceptRecord(observed)
                lastLocationAtEpochMillis = sample.capturedAtEpochMillis
                mutableHealth.value = TrackingHealth.Active(session, lastLocationAtEpochMillis)
            }
        }
        availabilityJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationSource.availability.collect { availability ->
                mutableHealth.value = availability.toTrackingHealth(session, lastLocationAtEpochMillis)
            }
        }
        locationSource.start(MOVING_LOCATION_POLICY)
        return StartResult.Started
    }

    suspend fun stop(reason: String) {
        sampleJob?.cancelAndJoin()
        availabilityJob?.cancelAndJoin()
        sampleJob = null
        availabilityJob = null
        if (activeSession != null) locationSource.stop()
        activeSession = null
        lastLocationAtEpochMillis = null
        mutableHealth.value = TrackingHealth.Stopped(reason)
    }

    private fun LocationAvailability.toTrackingHealth(
        session: ActiveTripSession,
        lastLocationAtEpochMillis: Long?,
    ): TrackingHealth = when (this) {
        LocationAvailability.Available -> TrackingHealth.Active(session, lastLocationAtEpochMillis)
        is LocationAvailability.PermissionMissing -> TrackingHealth.permissionRevoked(session, permission)
        LocationAvailability.ServicesDisabled -> TrackingHealth.Degraded(
            session,
            setOf(TrackingIssue.LocationServicesDisabled),
        )
        is LocationAvailability.Unavailable -> TrackingHealth.Degraded(
            session,
            setOf(TrackingIssue.LocationUnavailable(reason)),
        )
    }

    private companion object {
        val MOVING_LOCATION_POLICY = LocationRequestPolicy(intervalMillis = 15_000L)
    }
}
