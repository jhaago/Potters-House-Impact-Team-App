package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.EnvelopeCodec
import org.pottershouse.impactteam.protocol.MessagePriority
import org.pottershouse.impactteam.protocol.locationEnvelope
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.TrackingLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncPlannerTest {
    private val now = 1_700_000_100_000L
    private val peer = DeviceId("peer")

    @Test
    fun onlyRecordsNewerThanPeerHighWaterMarkAreSelected() {
        val ledger = ledgerWithSequences(1, 2, 3)
        val planner = SyncPlanner(ledger)
        val digest = SyncDigest(mapOf(DeviceId("device-1") to 2), emptySet())

        val batch = planner.plan(peer, digest, now)

        assertEquals(listOf(3L), batch.records.map { it.envelope.originSequence })
    }

    @Test
    fun urgentPrioritySortsBeforeNewerNormalRecords() {
        val ledger = TrackingLedger()
        ledger.accept(envelope(1, "urgent-device", MessagePriority.URGENT), now, ArrivalPath.LOCAL, null)
        ledger.accept(envelope(9, "normal-device", MessagePriority.NORMAL), now + 1, ArrivalPath.LOCAL, null)

        val batch = SyncPlanner(ledger).plan(peer, SyncDigest.EMPTY, now + 2)

        assertEquals(MessagePriority.URGENT, batch.records.first().envelope.priority)
    }

    @Test
    fun recordAndByteLimitsAreRespectedWithoutSplittingARecord() {
        val ledger = ledgerWithSequences(1, 2, 3)
        val planner = SyncPlanner(ledger)

        assertEquals(1, planner.plan(peer, SyncDigest.EMPTY, now, maxRecords = 1).records.size)
        val oneRecordBytes = EnvelopeCodec.encode(envelope(3)).encodeToByteArray().size
        val tooSmall = planner.plan(peer, SyncDigest.EMPTY, now, maxBytes = oneRecordBytes - 1)
        assertTrue(tooSmall.records.isEmpty())
    }

    @Test
    fun expiredRecordsAndKnownUrgentRecordsAreExcluded() {
        val ledger = TrackingLedger()
        val urgent = envelope(1, priority = MessagePriority.URGENT)
        ledger.accept(urgent, now, ArrivalPath.LOCAL, null)

        val digest = SyncDigest(emptyMap(), setOf(urgent.recordId))

        assertTrue(SyncPlanner(ledger).plan(peer, digest, urgent.expiresAtEpochMillis).records.isEmpty())
    }

    @Test
    fun digestReportsHighWaterMarksAndImportantIds() {
        val ledger = TrackingLedger()
        val urgent = envelope(2, priority = MessagePriority.URGENT)
        ledger.accept(envelope(1), now, ArrivalPath.LOCAL, null)
        ledger.accept(urgent, now + 1, ArrivalPath.LOCAL, null)

        val digest = SyncPlanner(ledger).digest(now + 2)

        assertEquals(2, digest.highestSequenceByOrigin[DeviceId("device-1")])
        assertEquals(setOf(urgent.recordId), digest.importantRecordIds)
    }

    private fun ledgerWithSequences(vararg sequences: Long) = TrackingLedger().also { ledger ->
        sequences.forEach { ledger.accept(envelope(it), now + it, ArrivalPath.LOCAL, null) }
    }
}

internal fun envelope(
    sequence: Long,
    deviceId: String = "device-1",
    priority: MessagePriority = MessagePriority.NORMAL,
) = locationEnvelope().copy(
    recordId = RecordId("record-$deviceId-$sequence"),
    originDeviceId = DeviceId(deviceId),
    originSequence = sequence,
    priority = priority,
)
