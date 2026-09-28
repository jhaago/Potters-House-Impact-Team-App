package org.pottershouse.impactteam.android.tracking

import kotlinx.coroutines.CoroutineScope
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.state.TrackingLedger
import org.pottershouse.impactteam.sync.SyncPlanner
import org.pottershouse.impactteam.transport.PeerTransport
import org.pottershouse.impactteam.transport.TransportRouter

class TrackingServiceRuntime(
    private val loadLedger: suspend (TripId) -> TrackingLedger,
    private val acceptRecord: suspend (ObservedEnvelope) -> AcceptResult,
    private val startLocation: suspend (ActiveTripSession) -> Unit,
    private val stopLocation: suspend (String) -> Unit,
    private val peerTransport: PeerTransport,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long,
) {
    private var activeSession: ActiveTripSession? = null
    private var router: TransportRouter? = null

    suspend fun start(session: ActiveTripSession) {
        if (activeSession == session) return
        if (activeSession != null) stop("Active trip changed")

        val planner = SyncPlanner(loadLedger(session.tripId))
        val newRouter = TransportRouter(
            transport = peerTransport,
            scope = scope,
            nowEpochMillis = nowEpochMillis,
            digestProvider = { planner.digest(nowEpochMillis()) },
            batchPlanner = { peerId, digest ->
                planner.plan(peerId.deviceId, digest, nowEpochMillis())
            },
            acceptRecord = acceptRecord,
        )
        activeSession = session
        router = newRouter
        startLocation(session)
        newRouter.start(session)
    }

    suspend fun stop(reason: String) {
        if (activeSession == null) return
        router?.stop()
        router = null
        stopLocation(reason)
        activeSession = null
    }
}
