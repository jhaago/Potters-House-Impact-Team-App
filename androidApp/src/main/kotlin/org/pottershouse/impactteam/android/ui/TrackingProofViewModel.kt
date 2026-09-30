package org.pottershouse.impactteam.android.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TrackingHealth
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.Freshness
import org.pottershouse.impactteam.state.MemberTrackingState
import org.pottershouse.impactteam.state.MonitoredSeparationAssessment
import org.pottershouse.impactteam.state.SeparationAnalyzer
import org.pottershouse.impactteam.state.SeparationLevel
import org.pottershouse.impactteam.state.SeparationMonitor
import org.pottershouse.impactteam.transport.PeerId
import org.pottershouse.impactteam.transport.TransportEvent
import kotlin.math.roundToInt

data class ProofSetup(
    val tripId: String,
    val tripName: String,
    val teamId: String,
    val memberId: String,
    val deviceId: String,
) {
    fun isComplete(): Boolean = listOf(tripId, tripName, teamId, memberId, deviceId).all { it.isNotBlank() }

    fun toSession(activatedAtEpochMillis: Long): ActiveTripSession = ActiveTripSession(
        tripId = TripId(tripId.trim()),
        teamId = TeamId(teamId.trim()),
        memberId = MemberId(memberId.trim()),
        deviceId = DeviceId(deviceId.trim()),
        tripName = tripName.trim(),
        activatedAtEpochMillis = activatedAtEpochMillis,
    )
}

data class MemberStateRowModel(
    val memberId: String,
    val ageLabel: String,
    val freshnessLabel: String,
    val batteryLabel: String,
    val accuracyLabel: String,
    val arrivalLabel: String,
    val isLastKnown: Boolean,
    val separationLevel: SeparationLevel = SeparationLevel.INSUFFICIENT_DATA,
    val rawSeparationLevel: SeparationLevel = SeparationLevel.INSUFFICIENT_DATA,
    val separationDistanceMeters: Int? = null,
    val separationMovingAway: Boolean = false,
)

data class AuthenticationCodeUi(
    val peerId: String,
    val digits: String,
)

data class DiagnosticsUiState(
    val nearbyCount: Int = 0,
    val lastExchangeAge: String = "Never",
    val acceptedCount: Int = 0,
    val duplicateCount: Int = 0,
    val rejectedCount: Int = 0,
    val failures: List<String> = emptyList(),
    val batteryPercent: Int? = null,
    val authenticationDigits: List<AuthenticationCodeUi> = emptyList(),
) {
    fun redactedText(): String = buildString {
        appendLine("Impact Team tracking diagnostics")
        appendLine("Nearby phones: $nearbyCount")
        appendLine("Last exchange: $lastExchangeAge")
        appendLine("Accepted: $acceptedCount")
        appendLine("Duplicates: $duplicateCount")
        appendLine("Rejected: $rejectedCount")
        appendLine("Battery: ${batteryPercent?.let { "$it%" } ?: "Unknown"}")
        if (failures.isNotEmpty()) appendLine("Failures: ${failures.joinToString(" | ")}")
    }.trimEnd()
}

data class TrackingProofUiState(
    val setup: ProofSetup,
    val health: TrackingHealth = TrackingHealth.Idle,
    val members: List<MemberStateRowModel> = emptyList(),
    val diagnostics: DiagnosticsUiState = DiagnosticsUiState(),
    val setupError: String? = null,
)

data class TrackingProofTelemetrySnapshot(
    val health: TrackingHealth = TrackingHealth.Idle,
    val connectedPeers: Set<PeerId> = emptySet(),
    val lastExchangeAtEpochMillis: Long? = null,
    val acceptedCount: Int = 0,
    val duplicateCount: Int = 0,
    val rejectedCount: Int = 0,
    val transportFailures: List<String> = emptyList(),
    val batteryPercent: Int? = null,
    val authenticationDigits: Map<PeerId, String> = emptyMap(),
)

class TrackingProofTelemetryStore {
    private val mutableState = MutableStateFlow(TrackingProofTelemetrySnapshot())
    val state: StateFlow<TrackingProofTelemetrySnapshot> = mutableState.asStateFlow()

    fun begin(session: ActiveTripSession) {
        mutableState.value = TrackingProofTelemetrySnapshot(health = TrackingHealth.Active(session, null))
    }

    fun recordHealth(health: TrackingHealth) {
        mutableState.update { it.copy(health = health) }
    }

    fun recordTransport(event: TransportEvent, nowEpochMillis: Long) {
        mutableState.update { current ->
            when (event) {
                is TransportEvent.Connected -> current.copy(
                    connectedPeers = current.connectedPeers + event.peerId,
                )
                is TransportEvent.Disconnected -> current.copy(
                    connectedPeers = current.connectedPeers - event.peerId,
                    authenticationDigits = current.authenticationDigits - event.peerId,
                )
                is TransportEvent.AuthenticationRequired -> current.copy(
                    authenticationDigits = current.authenticationDigits + (event.peerId to event.token),
                )
                is TransportEvent.MessageReceived -> current.copy(lastExchangeAtEpochMillis = nowEpochMillis)
                is TransportEvent.Failure -> current.copy(
                    transportFailures = (current.transportFailures + event.reason).takeLast(MAX_FAILURES),
                )
                is TransportEvent.Discovered -> current
            }
        }
    }

    fun recordAccept(result: AcceptResult) {
        mutableState.update { current ->
            when (result) {
                is AcceptResult.Accepted -> current.copy(
                    acceptedCount = current.acceptedCount + 1,
                    batteryPercent = result.observed.envelope.payload.batteryPercent,
                    lastExchangeAtEpochMillis = if (result.observed.arrivalPath == ArrivalPath.LOCAL) {
                        current.lastExchangeAtEpochMillis
                    } else {
                        result.observed.receivedAtEpochMillis
                    },
                )
                is AcceptResult.Duplicate -> current.copy(duplicateCount = current.duplicateCount + 1)
                is AcceptResult.RejectedInvalid,
                is AcceptResult.StaleSequence,
                -> current.copy(rejectedCount = current.rejectedCount + 1)
            }
        }
    }

    fun stop(reason: String) {
        mutableState.update {
            it.copy(
                health = TrackingHealth.Stopped(reason),
                connectedPeers = emptySet(),
                authenticationDigits = emptyMap(),
            )
        }
    }

    private companion object {
        const val MAX_FAILURES = 8
    }
}

val TrackingProofTelemetry = TrackingProofTelemetryStore()

class TrackingProofViewModel(
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long,
    private val missingPermissions: () -> Set<TrackingPermission>,
    private val startTracking: (ActiveTripSession) -> Unit,
    private val stopTracking: () -> Unit,
    private val loadMembers: suspend (TripId) -> List<MemberTrackingState>,
    telemetry: StateFlow<TrackingProofTelemetrySnapshot>,
) {
    private val mutableState = MutableStateFlow(
        TrackingProofUiState(
            setup = ProofSetup("impact-proof", "Impact Team Tracking Proof", "blue", "", ""),
        ),
    )
    val state: StateFlow<TrackingProofUiState> = mutableState.asStateFlow()

    private val separationAnalyzer = SeparationAnalyzer()
    private var separationMonitor = SeparationMonitor()
    private var activeSession: ActiveTripSession? = null
    private var pollingJob: Job? = null

    init {
        scope.launch {
            telemetry.collect { snapshot ->
                mutableState.update { current ->
                    current.copy(
                        health = if (activeSession != null && snapshot.health !is TrackingHealth.Idle) {
                            snapshot.health
                        } else {
                            current.health
                        },
                        diagnostics = snapshot.toUi(nowEpochMillis()),
                    )
                }
            }
        }
    }

    fun updateSetup(setup: ProofSetup) {
        if (activeSession == null) mutableState.update { it.copy(setup = setup, setupError = null) }
    }

    fun start(setup: ProofSetup) {
        updateSetup(setup)
        if (!setup.isComplete()) {
            mutableState.update { it.copy(setupError = "Trip, team and member details are required") }
            return
        }
        val permissions = missingPermissions()
        if (permissions.isNotEmpty()) {
            mutableState.update { it.copy(health = TrackingHealth.PermissionRequired(permissions)) }
            return
        }

        val session = setup.toSession(nowEpochMillis())
        activeSession = session
        separationMonitor = SeparationMonitor()
        mutableState.update {
            it.copy(health = TrackingHealth.Active(session, null), setupError = null)
        }
        startTracking(session)
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && activeSession == session) {
                refreshMembers(session.tripId)
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        if (activeSession == null) return
        pollingJob?.cancel()
        pollingJob = null
        stopTracking()
        activeSession = null
        mutableState.update {
            it.copy(health = TrackingHealth.Stopped("Stopped by member"))
        }
    }

    private suspend fun refreshMembers(tripId: TripId) {
        val now = nowEpochMillis()
        val memberStates = loadMembers(tripId)
        val separationByMember = separationMonitor
            .update(separationAnalyzer.assess(memberStates), now)
            .associateBy { it.memberId }
        val rows = memberStates
            .map { member -> member.toRow(now, separationByMember[member.memberId]) }
            .sortedBy { it.memberId }
        mutableState.update { it.copy(members = rows) }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_000L
    }
}

private fun MemberTrackingState.toRow(
    nowEpochMillis: Long,
    separation: MonitoredSeparationAssessment?,
): MemberStateRowModel {
    val payload = observed.envelope.payload
    return MemberStateRowModel(
        memberId = memberId.value,
        ageLabel = formatAge(observed.receivedAtEpochMillis, nowEpochMillis),
        freshnessLabel = when (freshness) {
            Freshness.CURRENT -> "Current"
            Freshness.BECOMING_STALE -> "Becoming stale"
            Freshness.STALE -> "Last known"
        },
        batteryLabel = "${payload.batteryPercent}% battery",
        accuracyLabel = "±${payload.accuracyMeters.roundToInt()} m",
        arrivalLabel = when (observed.arrivalPath) {
            ArrivalPath.LOCAL -> "Local"
            ArrivalPath.INTERNET -> "Internet"
            ArrivalPath.DIRECT_PEER -> "Direct peer"
            ArrivalPath.RELAYED -> "Relayed (${observed.relayCount} hops)"
        },
        isLastKnown = freshness == Freshness.STALE,
        separationLevel = separation?.level ?: SeparationLevel.INSUFFICIENT_DATA,
        rawSeparationLevel = separation?.rawLevel ?: SeparationLevel.INSUFFICIENT_DATA,
        separationDistanceMeters = separation?.evidenceDistanceMeters?.roundToInt(),
        separationMovingAway = separation?.movingAway == true,
    )
}

private fun TrackingProofTelemetrySnapshot.toUi(nowEpochMillis: Long): DiagnosticsUiState = DiagnosticsUiState(
    nearbyCount = connectedPeers.size,
    lastExchangeAge = lastExchangeAtEpochMillis?.let { formatAge(it, nowEpochMillis) } ?: "Never",
    acceptedCount = acceptedCount,
    duplicateCount = duplicateCount,
    rejectedCount = rejectedCount,
    failures = transportFailures,
    batteryPercent = batteryPercent,
    authenticationDigits = authenticationDigits
        .map { (peer, digits) -> AuthenticationCodeUi(peer.deviceId.value, digits) }
        .sortedBy { it.peerId },
)

private fun formatAge(timestampEpochMillis: Long, nowEpochMillis: Long): String {
    val seconds = ((nowEpochMillis - timestampEpochMillis).coerceAtLeast(0) / 1_000)
    return when {
        seconds < 60 -> "$seconds sec ago"
        seconds < 3_600 -> "${seconds / 60} min ago"
        else -> "${seconds / 3_600} hr ago"
    }
}
