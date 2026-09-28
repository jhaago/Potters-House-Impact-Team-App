package org.pottershouse.impactteam.android.tracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.GeoPoint
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TrackingIssue
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.transport.LocationAvailability
import org.pottershouse.impactteam.transport.LocationRequestPolicy
import org.pottershouse.impactteam.transport.LocationSample
import org.pottershouse.impactteam.transport.LocationSource

class ActiveTripControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun cleanUp() {
        scope.cancel()
    }

    @Test
    fun startRequiresAnExplicitActiveTripSession() = runBlocking {
        val source = FakeLocationSource()
        val controller = controller(source)

        val result = controller.start(null)

        assertTrue(result is StartResult.SessionRequired)
        assertEquals(0, source.startCount)
        assertTrue(controller.health.value is TrackingHealth.Idle)
    }

    @Test
    fun startingTheSameSessionTwiceIsIdempotent() = runBlocking {
        val source = FakeLocationSource()
        val controller = controller(source)

        assertTrue(controller.start(session) is StartResult.Started)
        assertTrue(controller.start(session) is StartResult.AlreadyActive)

        assertEquals(1, source.startCount)
        assertEquals(15_000L, source.lastPolicy?.intervalMillis)
    }

    @Test
    fun stopCancelsFurtherLocationProduction() = runBlocking {
        val source = FakeLocationSource()
        val accepted = mutableListOf<ObservedEnvelope>()
        val controller = controller(source, accepted)
        controller.start(session)
        source.emit(sample(capturedAt = 100))
        yield()
        assertEquals(1, accepted.size)

        controller.stop("Trip ended by member")
        source.emit(sample(capturedAt = 200))
        yield()

        assertEquals(1, accepted.size)
        assertEquals(1, source.stopCount)
        assertEquals(TrackingHealth.Stopped("Trip ended by member"), controller.health.value)
    }

    @Test
    fun permissionRevocationPublishesDegradedHealth() = runBlocking {
        val source = FakeLocationSource()
        val controller = controller(source)
        controller.start(session)

        source.availabilityState.value =
            LocationAvailability.PermissionMissing(TrackingPermission.BACKGROUND_LOCATION)
        yield()

        assertEquals(
            TrackingHealth.Degraded(
                session,
                setOf(TrackingIssue.PermissionRevoked(TrackingPermission.BACKGROUND_LOCATION)),
            ),
            controller.health.value,
        )
    }

    private fun controller(
        source: FakeLocationSource,
        accepted: MutableList<ObservedEnvelope> = mutableListOf(),
    ) = ActiveTripController(
        locationSource = source,
        envelopeFactory = LocationEnvelopeFactory(
            nextSequence = { 1L },
            nowEpochMillis = { 1_000L },
        ),
        acceptRecord = {
            accepted += it
            AcceptResult.Accepted(it, replacedPrevious = false)
        },
        scope = scope,
    )

    private fun sample(capturedAt: Long) = LocationSample(
        point = GeoPoint(-17.8252, 31.0335),
        capturedAtEpochMillis = capturedAt,
        horizontalAccuracyMeters = 12.0,
        batteryPercent = 76,
    )

    private class FakeLocationSource : LocationSource {
        private val sampleFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 4)
        val availabilityState = MutableStateFlow<LocationAvailability>(LocationAvailability.Available)
        var startCount = 0
        var stopCount = 0
        var lastPolicy: LocationRequestPolicy? = null

        override val samples: Flow<LocationSample> = sampleFlow
        override val availability: StateFlow<LocationAvailability> = availabilityState

        override suspend fun start(policy: LocationRequestPolicy) {
            startCount += 1
            lastPolicy = policy
        }

        override suspend fun stop() {
            stopCount += 1
        }

        fun emit(sample: LocationSample) {
            check(sampleFlow.tryEmit(sample))
        }
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
