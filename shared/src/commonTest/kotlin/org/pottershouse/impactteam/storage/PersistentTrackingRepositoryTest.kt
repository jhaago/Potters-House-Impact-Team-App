package org.pottershouse.impactteam.storage

import kotlinx.coroutines.test.runTest
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.EnvelopeCodec
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.protocol.locationEnvelope
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersistentTrackingRepositoryTest {
    private val now = 1_700_000_100_000L
    private val tripId = TripId("trip-1")

    @Test
    fun restartRestoresValidRecordsAndRelayMetadata() = runTest {
        val dao = FakeTrackingRecordDao()
        val first = PersistentTrackingRepository(dao) { now }
        val observed = observed(sequence = 7, relayCount = 2)

        assertIs<AcceptResult.Accepted>(first.accept(observed))

        val restored = PersistentTrackingRepository(dao) { now }.loadLedger(tripId)
        val restoredObservation = restored.snapshot(now).single().observed
        assertEquals(7, restoredObservation.envelope.originSequence)
        assertEquals(2, restoredObservation.relayCount)
        assertEquals(listOf(DeviceId("peer-a"), DeviceId("peer-b")), restoredObservation.recentPeerIds)
    }

    @Test
    fun nextOriginSequenceIsMonotonicAcrossRepositoryRestarts() = runTest {
        val dao = FakeTrackingRecordDao()

        assertEquals(1, PersistentTrackingRepository(dao) { now }.nextOriginSequence(DeviceId("device-1")))
        assertEquals(2, PersistentTrackingRepository(dao) { now }.nextOriginSequence(DeviceId("device-1")))
        assertEquals(1, PersistentTrackingRepository(dao) { now }.nextOriginSequence(DeviceId("device-2")))
    }

    @Test
    fun duplicateInsertIsIdempotent() = runTest {
        val dao = FakeTrackingRecordDao()
        val repository = PersistentTrackingRepository(dao) { now }
        val observed = observed(sequence = 4)

        assertIs<AcceptResult.Accepted>(repository.accept(observed))
        assertIs<AcceptResult.Duplicate>(repository.accept(observed.copy(receivedAtEpochMillis = now + 1)))
        assertEquals(1, dao.activeRecords().size)
    }

    @Test
    fun compactRemovesExpiredRecords() = runTest {
        val dao = FakeTrackingRecordDao()
        val repository = PersistentTrackingRepository(dao) { now }
        dao.insertRecord(entity(envelope(sequence = 1).copy(expiresAtEpochMillis = now - 1)))
        dao.insertRecord(entity(envelope(sequence = 2).copy(expiresAtEpochMillis = now + 60_000)))

        val result = repository.compact(now)

        assertEquals(1, result.expiredRecordsRemoved)
        assertEquals(listOf(2L), dao.activeRecords().map { it.originSequence })
    }

    @Test
    fun corruptEnvelopeIsQuarantinedWithoutLosingValidRows() = runTest {
        val dao = FakeTrackingRecordDao()
        dao.insertRecord(entity(envelope(sequence = 1)))
        dao.insertRecord(entity(envelope(sequence = 2)).copy(canonicalEnvelopeJson = "{not-json"))

        val ledger = PersistentTrackingRepository(dao) { now }.loadLedger(tripId)

        assertEquals(listOf(1L), ledger.syncCandidates(now).map { it.envelope.originSequence })
        val quarantined = assertNotNull(dao.record(RecordId("record-2")))
        assertTrue(quarantined.quarantineReason?.contains("malformed", ignoreCase = true) == true)
    }

    @Test
    fun restartRejectsLowerSequenceWithoutPersistingIt() = runTest {
        val dao = FakeTrackingRecordDao()
        val first = PersistentTrackingRepository(dao) { now }
        assertIs<AcceptResult.Accepted>(first.accept(observed(sequence = 10)))

        val restarted = PersistentTrackingRepository(dao) { now }
        restarted.loadLedger(tripId)
        val result = restarted.accept(observed(sequence = 9))

        assertIs<AcceptResult.StaleSequence>(result)
        assertEquals(listOf(10L), dao.activeRecords().map { it.originSequence })
    }

    @Test
    fun indexedMetadataMismatchIsQuarantined() = runTest {
        val dao = FakeTrackingRecordDao()
        dao.insertRecord(entity(envelope(sequence = 3)).copy(originSequence = 99))

        val ledger = PersistentTrackingRepository(dao) { now }.loadLedger(tripId)

        assertTrue(ledger.snapshot(now).isEmpty())
        assertTrue(dao.record(RecordId("record-3"))?.quarantineReason?.contains("metadata") == true)
    }

    @Test
    fun validRetransmissionReplacesQuarantinedConflict() = runTest {
        val dao = FakeTrackingRecordDao()
        val corrupt = entity(envelope(sequence = 5)).copy(canonicalEnvelopeJson = "{not-json")
        dao.insertRecord(corrupt)
        val repository = PersistentTrackingRepository(dao) { now }
        repository.loadLedger(tripId)

        val result = repository.accept(observed(sequence = 5))

        assertIs<AcceptResult.Accepted>(result)
        assertEquals(null, dao.record(RecordId("record-5"))?.quarantineReason)
    }

    @Test
    fun compactionPreservesPersistedOriginHighWaterMark() = runTest {
        val dao = FakeTrackingRecordDao()
        val repository = PersistentTrackingRepository(dao) { now }
        assertIs<AcceptResult.Accepted>(repository.accept(observed(sequence = 10)))
        repository.compact(now + 120_000)

        val replay = repository.accept(observed(sequence = 9))

        assertIs<AcceptResult.StaleSequence>(replay)
        assertEquals(10, replay.highestAcceptedSequence)
    }

    @Test
    fun restartRestoresHistoryByOriginSequenceAcrossClockCorrection() = runTest {
        val dao = FakeTrackingRecordDao()
        val repository = PersistentTrackingRepository(dao) { now }
        repository.accept(observed(sequence = 1).copy(receivedAtEpochMillis = now + 100))
        repository.accept(observed(sequence = 2).copy(receivedAtEpochMillis = now))

        val restored = PersistentTrackingRepository(dao) { now }.loadLedger(tripId)

        assertEquals(listOf(2L, 1L), restored.syncCandidates(now).map { it.envelope.originSequence })
        assertEquals(2, restored.snapshot(now).single().observed.envelope.originSequence)
    }

    private fun observed(sequence: Long, relayCount: Int = 0) = ObservedEnvelope(
        envelope = envelope(sequence),
        receivedAtEpochMillis = now,
        arrivalPath = ArrivalPath.RELAYED,
        suppliedByPeerId = DeviceId("peer-b"),
        relayCount = relayCount,
        recentPeerIds = listOf(DeviceId("peer-a"), DeviceId("peer-b")),
    )

    private fun envelope(sequence: Long): TrackingEnvelope = locationEnvelope().copy(
        recordId = RecordId("record-$sequence"),
        tripId = tripId,
        originDeviceId = DeviceId("device-1"),
        originSequence = sequence,
        expiresAtEpochMillis = now + 60_000,
    )

    private fun entity(envelope: TrackingEnvelope) = TrackingRecordEntity.from(
        observed = ObservedEnvelope(
            envelope = envelope,
            receivedAtEpochMillis = now,
            arrivalPath = ArrivalPath.LOCAL,
            suppliedByPeerId = null,
        ),
        canonicalEnvelopeJson = EnvelopeCodec.encode(envelope),
    )
}

private class FakeTrackingRecordDao : TrackingRecordDao {
    private val records = linkedMapOf<String, TrackingRecordEntity>()
    private val sequences = mutableMapOf<String, Long>()
    private val highWaterMarks = mutableMapOf<String, Long>()

    override suspend fun insertRecord(record: TrackingRecordEntity): Long {
        val hasRecord = records.containsKey(record.recordId)
        val hasOriginSequence = records.values.any {
            it.originDeviceId == record.originDeviceId && it.originSequence == record.originSequence
        }
        if (hasRecord || hasOriginSequence) return -1
        records[record.recordId] = record
        return records.size.toLong()
    }

    override suspend fun recordsForTrip(tripId: String): List<TrackingRecordEntity> = records.values
        .filter { it.tripId == tripId && it.quarantineReason == null }
        .sortedWith(compareBy<TrackingRecordEntity> { it.originDeviceId }.thenBy { it.originSequence })

    override suspend fun activeRecordCount(recordId: String): Int = records.values.count {
        it.recordId == recordId && it.quarantineReason == null
    }

    override suspend fun acceptedOriginHighWater(deviceId: String): Long? = highWaterMarks[deviceId]

    override suspend fun initializeOriginHighWater(highWater: OriginHighWaterEntity): Long {
        if (highWaterMarks.containsKey(highWater.deviceId)) return -1
        highWaterMarks[highWater.deviceId] = highWater.highestAcceptedSequence
        return highWaterMarks.size.toLong()
    }

    override suspend fun raiseOriginHighWater(deviceId: String, sequence: Long): Int {
        val current = highWaterMarks[deviceId] ?: return 0
        if (sequence > current) highWaterMarks[deviceId] = sequence
        return 1
    }

    override suspend fun deleteQuarantinedConflicts(
        recordId: String,
        deviceId: String,
        originSequence: Long,
    ): Int {
        val conflicts = records.values.filter {
            it.quarantineReason != null &&
                (it.recordId == recordId ||
                    (it.originDeviceId == deviceId && it.originSequence == originSequence))
        }.map { it.recordId }
        conflicts.forEach(records::remove)
        return conflicts.size
    }

    override suspend fun allocateNextOriginSequence(deviceId: String): Long {
        val next = (sequences[deviceId] ?: 0L) + 1L
        sequences[deviceId] = next
        return next
    }

    override suspend fun initializeOriginSequence(sequence: OriginSequenceEntity): Long {
        if (sequences.containsKey(sequence.deviceId)) return -1
        sequences[sequence.deviceId] = sequence.lastSequence
        return sequences.size.toLong()
    }

    override suspend fun incrementOriginSequence(deviceId: String): Int {
        val current = sequences[deviceId] ?: return 0
        sequences[deviceId] = current + 1
        return 1
    }

    override suspend fun currentOriginSequence(deviceId: String): Long? = sequences[deviceId]

    override suspend fun deleteExpired(nowEpochMillis: Long): Int {
        val expired = records.values.filter { it.expiresAtEpochMillis <= nowEpochMillis }.map { it.recordId }
        expired.forEach(records::remove)
        return expired.size
    }

    override suspend fun quarantine(recordId: String, reason: String): Int {
        val existing = records[recordId] ?: return 0
        records[recordId] = existing.copy(quarantineReason = reason)
        return 1
    }

    fun activeRecords() = records.values.filter { it.quarantineReason == null }

    fun record(recordId: RecordId) = records[recordId.value]
}
