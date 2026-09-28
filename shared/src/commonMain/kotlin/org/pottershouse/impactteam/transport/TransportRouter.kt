package org.pottershouse.impactteam.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
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
    private val digestProvider: () -> SyncDigest,
    private val batchPlanner: (PeerId, SyncDigest) -> SyncBatch,
    private val acceptRecord: suspend (ObservedEnvelope) -> AcceptResult,
) {
    private var activeSession: ActiveTripSession? = null
    private var eventJob: Job? = null

    suspend fun start(session: ActiveTripSession): Boolean {
        if (activeSession == session && eventJob?.isActive == true) return false
        if (activeSession != null) stop()
        activeSession = session
        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.events.collect { event ->
                runCatching { process(session, event) }
            }
        }
        transport.start(session)
        return true
    }

    suspend fun stop(): Boolean {
        if (activeSession == null) return false
        eventJob?.cancelAndJoin()
        eventJob = null
        transport.stop()
        activeSession = null
        return true
    }

    private suspend fun process(session: ActiveTripSession, event: TransportEvent) {
        when (event) {
            is TransportEvent.Connected -> transport.send(
                event.peerId,
                PeerMessage.Digest(digestProvider()),
            )
            is TransportEvent.MessageReceived -> processMessage(session, event.peerId, event.message)
            is TransportEvent.AuthenticationRequired,
            is TransportEvent.Discovered,
            is TransportEvent.Disconnected,
            is TransportEvent.Failure,
            -> Unit
        }
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
}
