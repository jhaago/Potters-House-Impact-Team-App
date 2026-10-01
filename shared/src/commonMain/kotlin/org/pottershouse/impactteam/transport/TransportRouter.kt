package org.pottershouse.impactteam.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.pottershouse.impactteam.domain.ActiveTripSession
import org.pottershouse.impactteam.protocol.EnvelopeValidator
import org.pottershouse.impactteam.protocol.ValidationResult
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.sync.SyncBatch
import org.pottershouse.impactteam.sync.SyncDigest

class TransportRouter(
    private val transport: PeerTransport,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long,
    private val digestProvider: suspend () -> SyncDigest,
    private val batchPlanner: suspend (PeerId, SyncDigest) -> SyncBatch,
    private val acceptRecord: suspend (ObservedEnvelope) -> AcceptResult,
    private val syncIntervalMillis: Long = DEFAULT_SYNC_INTERVAL_MILLIS,
) {
    private var activeSession: ActiveTripSession? = null
    private var eventJob: Job? = null
    private var syncJob: Job? = null
    private val connectedPeers = mutableSetOf<PeerId>()

    init {
        require(syncIntervalMillis > 0)
    }

    suspend fun start(session: ActiveTripSession): Boolean {
        if (activeSession == session && eventJob?.isActive == true) return false
        if (activeSession != null) stop()
        activeSession = session
        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.events.collect { event ->
                runCatching { process(session, event) }
            }
        }
        syncJob = scope.launch {
            while (isActive && activeSession == session) {
                delay(syncIntervalMillis)
                connectedPeers.toList().forEach { peerId ->
                    runCatching { sendDigest(peerId) }
                }
            }
        }
        transport.start(session)
        return true
    }

    suspend fun stop(): Boolean {
        if (activeSession == null) return false
        syncJob?.cancelAndJoin()
        syncJob = null
        eventJob?.cancelAndJoin()
        eventJob = null
        transport.stop()
        connectedPeers.clear()
        activeSession = null
        return true
    }

    private suspend fun process(session: ActiveTripSession, event: TransportEvent) {
        when (event) {
            is TransportEvent.Connected -> {
                connectedPeers += event.peerId
                sendDigest(event.peerId)
            }
            is TransportEvent.MessageReceived -> processMessage(session, event.peerId, event.message)
            is TransportEvent.Disconnected -> connectedPeers -= event.peerId
            is TransportEvent.AuthenticationRequired,
            is TransportEvent.Discovered,
            is TransportEvent.Failure,
            -> Unit
        }
    }

    private suspend fun sendDigest(peerId: PeerId) {
        transport.send(
            peerId,
            PeerMessage.Digest(digestProvider()),
        )
    }

    private suspend fun processMessage(
        session: ActiveTripSession,
        peerId: PeerId,
        message: PeerMessage,
    ) {
        when (message) {
            is PeerMessage.Digest -> {
                val batch = batchPlanner(peerId, message.digest)
                if (batch.records.isNotEmpty()) {
                    transport.send(
                        peerId,
                        PeerMessage.Batch(
                            batch.records.map {
                                PeerRecord(it.envelope, it.relayCount, it.recentPeerIds)
                            },
                        ),
                    )
                }
            }
            is PeerMessage.Batch -> acceptBatch(session, peerId, message)
            is PeerMessage.Error,
            is PeerMessage.Hello,
            -> Unit
        }
    }

    private suspend fun acceptBatch(
        session: ActiveTripSession,
        peerId: PeerId,
        batch: PeerMessage.Batch,
    ) {
        val receivedAt = nowEpochMillis()
        batch.records.forEach { record ->
            val envelope = record.envelope
            if (envelope.tripId != session.tripId) return@forEach
            if (EnvelopeValidator.validate(envelope, receivedAt) !is ValidationResult.Valid) return@forEach
            acceptRecord(
                ObservedEnvelope(
                    envelope = envelope,
                    receivedAtEpochMillis = receivedAt,
                    arrivalPath = if (record.relayCount <= 1) ArrivalPath.DIRECT_PEER else ArrivalPath.RELAYED,
                    suppliedByPeerId = peerId.deviceId,
                    relayCount = record.relayCount,
                    recentPeerIds = (record.recentPeerIds + peerId.deviceId).distinct().takeLast(8),
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_SYNC_INTERVAL_MILLIS = 15_000L
    }
}
