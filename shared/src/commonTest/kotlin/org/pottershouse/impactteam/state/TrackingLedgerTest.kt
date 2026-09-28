package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.protocol.locationEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TrackingLedgerTest {
    private val receivedAt = 1_700_000_100_000L

    @Test
    fun higherSequenceReplacesLowerSequence() {
        val ledger = TrackingLedger()
        ledger.accept(envelope(sequence = 7), receivedAt, ArrivalPath.DIRECT_PEER, DeviceId("peer-a"))

        val result = ledger.accept(envelope(sequence = 8), receivedAt + 1, ArrivalPath.RELAYED, DeviceId("peer-b"))

        assertIs<AcceptResult.Accepted>(result)
        assertEquals(8, ledger.snapshot(receivedAt + 1).single().observed.envelope.originSequence)
    }

    @Test
    fun lowerSequenceCannotReplaceNewerState() {
        val ledger = TrackingLedger()
        ledger.accept(envelope(sequence = 8), receivedAt, ArrivalPath.DIRECT_PEER, null)

        val result = ledger.accept(envelope(sequence = 7), receivedAt + 1, ArrivalPath.DIRECT_PEER, null)

        assertIs<AcceptResult.StaleSequence>(result)
        assertEquals(8, ledger.snapshot(receivedAt + 1).single().observed.envelope.originSequence)
    }

    @Test
    fun sameRecordIsDuplicate() {
        val ledger = TrackingLedger()
        val record = envelope(sequence = 7)
        ledger.accept(record, receivedAt, ArrivalPath.INTERNET, null)

        assertIs<AcceptResult.Duplicate>(ledger.accept(record, receivedAt + 1, ArrivalPath.RELAYED, DeviceId("peer")))
    }

    @Test
    fun futureClockDoesNotBeatHigherSequence() {
        val ledger = TrackingLedger()
        ledger.accept(
            envelope(sequence = 8, createdAt = receivedAt),
            receivedAt,
            ArrivalPath.DIRECT_PEER,
            null,
        )

        val result = ledger.accept(
            envelope(sequence = 7, createdAt = receivedAt + 86_400_000),
            receivedAt + 1,
            ArrivalPath.DIRECT_PEER,
            null,
        )

        assertIs<AcceptResult.StaleSequence>(result)
        assertEquals(8, ledger.snapshot(receivedAt + 1).single().observed.envelope.originSequence)
    }

    @Test
    fun higherSequenceFromSameOriginSurvivesReceiptClockCorrection() {
        val ledger = TrackingLedger()
        ledger.accept(envelope(sequence = 7), receivedAt + 100, ArrivalPath.LOCAL, null)

        ledger.accept(envelope(sequence = 8), receivedAt, ArrivalPath.LOCAL, null)

        assertEquals(8, ledger.snapshot(receivedAt + 100).single().observed.envelope.originSequence)
    }

    @Test
    fun newDeviceIdentityStartsIndependentSequence() {
        val ledger = TrackingLedger()
        ledger.accept(envelope(sequence = 50, deviceId = "old-device"), receivedAt, ArrivalPath.INTERNET, null)

        val result = ledger.accept(
            envelope(sequence = 1, deviceId = "new-device"),
            receivedAt + 1,
            ArrivalPath.DIRECT_PEER,
            null,
        )

        assertIs<AcceptResult.Accepted>(result)
        assertEquals("new-device", ledger.snapshot(receivedAt + 1).single().observed.envelope.originDeviceId.value)
        assertEquals(2, ledger.syncCandidates(receivedAt + 1).size)
    }

    @Test
    fun syncCandidatesKeepNewestFivePerOriginAndExcludeExpired() {
        val ledger = TrackingLedger()
        (1L..7L).forEach { sequence ->
            ledger.accept(envelope(sequence = sequence), receivedAt + sequence, ArrivalPath.LOCAL, null)
        }

        val candidates = ledger.syncCandidates(receivedAt + 10)

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), candidates.map { it.envelope.originSequence })
        assertEquals(emptyList(), ledger.syncCandidates(1_700_003_600_000L))
    }

    private fun envelope(
        sequence: Long,
        deviceId: String = "device-1",
        createdAt: Long = 1_700_000_000_000,
    ): TrackingEnvelope = locationEnvelope().copy(
        recordId = RecordId("record-$deviceId-$sequence"),
        originDeviceId = DeviceId(deviceId),
        originSequence = sequence,
        createdAtEpochMillis = createdAt,
    )
}
