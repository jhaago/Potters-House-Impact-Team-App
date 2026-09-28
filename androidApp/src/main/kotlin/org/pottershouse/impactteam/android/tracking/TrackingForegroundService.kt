package org.pottershouse.impactteam.android.tracking

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.pottershouse.impactteam.android.nearby.GoogleNearbyClient
import org.pottershouse.impactteam.android.nearby.NearbyPeerTransport
import org.pottershouse.impactteam.android.ui.TrackingProofTelemetry
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.storage.ImpactTeamDatabase
import org.pottershouse.impactteam.storage.PersistentTrackingRepository
import org.pottershouse.impactteam.storage.buildImpactTeamDatabase
import org.pottershouse.impactteam.storage.createImpactTeamDatabaseBuilder
import org.pottershouse.impactteam.state.ObservedEnvelope

class TrackingForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var database: ImpactTeamDatabase
    private lateinit var controller: ActiveTripController
    private lateinit var runtime: TrackingServiceRuntime
    private lateinit var peerTransport: NearbyPeerTransport

    override fun onCreate() {
        super.onCreate()
        database = buildImpactTeamDatabase(createImpactTeamDatabaseBuilder(this))
        val repository = PersistentTrackingRepository(database.trackingRecordDao(), System::currentTimeMillis)
        val monitoredAccept: suspend (ObservedEnvelope) -> org.pottershouse.impactteam.state.AcceptResult = { observed ->
            repository.accept(observed).also(TrackingProofTelemetry::recordAccept)
        }
        controller = ActiveTripController(
            locationSource = AndroidLocationSource(this),
            envelopeFactory = LocationEnvelopeFactory(
                nextSequence = repository::nextOriginSequence,
                nowEpochMillis = System::currentTimeMillis,
            ),
            acceptRecord = monitoredAccept,
            scope = serviceScope,
        )
        peerTransport = NearbyPeerTransport(GoogleNearbyClient(this), serviceScope)
        runtime = TrackingServiceRuntime(
            loadLedger = repository::loadLedger,
            acceptRecord = monitoredAccept,
            startLocation = { controller.start(it) },
            stopLocation = controller::stop,
            peerTransport = peerTransport,
            scope = serviceScope,
            nowEpochMillis = System::currentTimeMillis,
        )
        serviceScope.launch { controller.health.collect(TrackingProofTelemetry::recordHealth) }
        serviceScope.launch {
            peerTransport.events.collect { event ->
                TrackingProofTelemetry.recordTransport(event, System.currentTimeMillis())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = intent?.toActiveTripSession()
        if (session == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notifications = TrackingNotificationFactory(this)
        notifications.createChannel()
        val notification = notifications.create(session)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TrackingNotificationFactory.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(TrackingNotificationFactory.NOTIFICATION_ID, notification)
        }
        TrackingProofTelemetry.begin(session)
        serviceScope.launch { runtime.start(session) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runBlocking { runtime.stop("Tracking service stopped") }
        TrackingProofTelemetry.stop("Tracking service stopped")
        serviceScope.cancel()
        database.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val EXTRA_TEAM_ID = "team_id"
        private const val EXTRA_MEMBER_ID = "member_id"
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_TRIP_NAME = "trip_name"
        private const val EXTRA_ACTIVATED_AT = "activated_at"

        fun start(context: Context, session: ActiveTripSession) {
            context.startForegroundService(
                Intent(context, TrackingForegroundService::class.java)
                    .putExtra(EXTRA_TRIP_ID, session.tripId.value)
                    .putExtra(EXTRA_TEAM_ID, session.teamId.value)
                    .putExtra(EXTRA_MEMBER_ID, session.memberId.value)
                    .putExtra(EXTRA_DEVICE_ID, session.deviceId.value)
                    .putExtra(EXTRA_TRIP_NAME, session.tripName)
                    .putExtra(EXTRA_ACTIVATED_AT, session.activatedAtEpochMillis),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingForegroundService::class.java))
        }

        private fun Intent.toActiveTripSession(): ActiveTripSession? {
            val tripId = getStringExtra(EXTRA_TRIP_ID)?.takeIf(String::isNotBlank) ?: return null
            val teamId = getStringExtra(EXTRA_TEAM_ID)?.takeIf(String::isNotBlank) ?: return null
            val memberId = getStringExtra(EXTRA_MEMBER_ID)?.takeIf(String::isNotBlank) ?: return null
            val deviceId = getStringExtra(EXTRA_DEVICE_ID)?.takeIf(String::isNotBlank) ?: return null
            val tripName = getStringExtra(EXTRA_TRIP_NAME)?.takeIf(String::isNotBlank) ?: return null
            return ActiveTripSession(
                tripId = TripId(tripId),
                teamId = TeamId(teamId),
                memberId = MemberId(memberId),
                deviceId = DeviceId(deviceId),
                tripName = tripName,
                activatedAtEpochMillis = getLongExtra(EXTRA_ACTIVATED_AT, 0L),
            )
        }
    }
}
