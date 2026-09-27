package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.TrackingEnvelope

class TrackingLedger(
    private val freshnessPolicy: FreshnessPolicy = FreshnessPolicy(),
    private val retainedRecordsPerOrigin: Int = 5,
) {
    private val seenRecordIds = mutableSetOf<RecordId>()
    private val historyByOrigin = mutableMapOf<DeviceId, MutableList<ObservedEnvelope>>()
    private val currentByMember = mutableMapOf<MemberId, ObservedEnvelope>()

    init {
        require(retainedRecordsPerOrigin > 0)
    }

    fun accept(
        envelope: TrackingEnvelope,
        receivedAtEpochMillis: Long,
        arrivalPath: ArrivalPath,
        suppliedByPeerId: DeviceId?,
    ): AcceptResult = acceptInternal(
        envelope = envelope,
        receivedAtEpochMillis = receivedAtEpochMillis,
        arrivalPath = arrivalPath,
        suppliedByPeerId = suppliedByPeerId,
        relayCount = if (arrivalPath == ArrivalPath.RELAYED) 1 else 0,
        recentPeerIds = suppliedByPeerId?.let(::listOf).orEmpty(),
    )

    fun acceptForwarded(
        forwarded: ObservedEnvelope,
        receivedAtEpochMillis: Long,
        suppliedByPeerId: DeviceId,
    ): AcceptResult = acceptInternal(
        envelope = forwarded.envelope,
        receivedAtEpochMillis = receivedAtEpochMillis,
        arrivalPath = ArrivalPath.RELAYED,
        suppliedByPeerId = suppliedByPeerId,
        relayCount = forwarded.relayCount,
        recentPeerIds = (forwarded.recentPeerIds + suppliedByPeerId).distinct().takeLast(MAX_ROUTE_PEERS),
    )

    private fun acceptInternal(
        envelope: TrackingEnvelope,
        receivedAtEpochMillis: Long,
        arrivalPath: ArrivalPath,
        suppliedByPeerId: DeviceId?,
        relayCount: Int,
        recentPeerIds: List<DeviceId>,
    ): AcceptResult {
        if (envelope.recordId in seenRecordIds) return AcceptResult.Duplicate(envelope.recordId)
        seenRecordIds += envelope.recordId

        val originHistory = historyByOrigin.getOrPut(envelope.originDeviceId) { mutableListOf() }
        val highestSequence = originHistory.maxOfOrNull { it.envelope.originSequence }
        val observed = ObservedEnvelope(
            envelope = envelope,
            receivedAtEpochMillis = receivedAtEpochMillis,
            arrivalPath = arrivalPath,
            suppliedByPeerId = suppliedByPeerId,
            relayCount = relayCount,
            recentPeerIds = recentPeerIds,
        )
        if (highestSequence != null && envelope.originSequence <= highestSequence) {
            return AcceptResult.StaleSequence(observed, highestSequence)
        }

        originHistory += observed
        originHistory.sortByDescending { it.envelope.originSequence }
        while (originHistory.size > retainedRecordsPerOrigin) originHistory.removeLast()

        val previous = currentByMember[envelope.memberId]
        if (previous == null || receivedAtEpochMillis >= previous.receivedAtEpochMillis) {
            currentByMember[envelope.memberId] = observed
        }

        return AcceptResult.Accepted(observed, replacedPrevious = previous != null)
    }

    fun snapshot(nowEpochMillis: Long): List<MemberTrackingState> = currentByMember
        .map { (memberId, observed) ->
            MemberTrackingState(
                memberId = memberId,
                observed = observed,
                freshness = freshnessPolicy.classify(observed.receivedAtEpochMillis, nowEpochMillis),
            )
        }
        .sortedBy { it.memberId.value }

    fun syncCandidates(nowEpochMillis: Long): List<ObservedEnvelope> = historyByOrigin
        .entries
        .sortedBy { it.key.value }
        .flatMap { (_, history) ->
            history
                .asSequence()
                .filter { it.envelope.expiresAtEpochMillis > nowEpochMillis }
                .take(retainedRecordsPerOrigin)
                .toList()
        }

    private companion object {
        const val MAX_ROUTE_PEERS = 8
    }
}
