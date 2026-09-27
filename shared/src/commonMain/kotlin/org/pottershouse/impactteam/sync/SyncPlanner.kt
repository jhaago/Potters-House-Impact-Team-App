package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.protocol.EnvelopeCodec
import org.pottershouse.impactteam.protocol.MessagePriority
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.state.TrackingLedger

class SyncPlanner(
    private val ledger: TrackingLedger,
    private val relayPolicy: RelayPolicy = RelayPolicy(),
) {
    fun digest(nowEpochMillis: Long): SyncDigest {
        val candidates = ledger.syncCandidates(nowEpochMillis)
        return SyncDigest(
            highestSequenceByOrigin = candidates
                .groupBy { it.envelope.originDeviceId }
                .mapValues { (_, records) -> records.maxOf { it.envelope.originSequence } },
            importantRecordIds = candidates
                .asSequence()
                .filter { it.envelope.priority == MessagePriority.URGENT }
                .map { it.envelope.recordId }
                .toSet(),
        )
    }

    fun plan(
        peerId: DeviceId,
        peerDigest: SyncDigest,
        nowEpochMillis: Long,
        maxRecords: Int = 50,
        maxBytes: Int = 64 * 1024,
    ): SyncBatch {
        if (maxRecords <= 0 || maxBytes <= 0) return SyncBatch(emptyList(), 0)

        val eligible = ledger.syncCandidates(nowEpochMillis)
            .asSequence()
            .filter { relayPolicy.canOffer(it, peerId, nowEpochMillis) }
            .filter { observed ->
                val envelope = observed.envelope
                val peerSequence = peerDigest.highestSequenceByOrigin[envelope.originDeviceId] ?: 0
                envelope.originSequence > peerSequence ||
                    (envelope.priority == MessagePriority.URGENT && envelope.recordId !in peerDigest.importantRecordIds)
            }
            .sortedWith(
                compareByDescending<ObservedEnvelope> { it.envelope.priority == MessagePriority.URGENT }
                    .thenByDescending { it.envelope.createdAtEpochMillis }
                    .thenBy { it.envelope.recordId.value },
            )

        val selected = mutableListOf<ObservedEnvelope>()
        var encodedBytes = 0
        for (observed in eligible) {
            if (selected.size >= maxRecords) break
            val recordBytes = EnvelopeCodec.encode(observed.envelope).encodeToByteArray().size
            if (encodedBytes + recordBytes > maxBytes) continue
            selected += observed.copy(
                relayCount = observed.relayCount + 1,
                recentPeerIds = (observed.recentPeerIds + peerId).distinct().takeLast(MAX_ROUTE_PEERS),
            )
            encodedBytes += recordBytes
        }
        return SyncBatch(selected, encodedBytes)
    }

    private companion object {
        const val MAX_ROUTE_PEERS = 8
    }
}
