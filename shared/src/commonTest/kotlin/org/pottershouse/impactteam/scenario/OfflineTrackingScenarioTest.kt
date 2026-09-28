package org.pottershouse.impactteam.scenario

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.LocationPayload
import org.pottershouse.impactteam.protocol.MessagePriority
import org.pottershouse.impactteam.protocol.TrackingEnvelope
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.state.TrackingLedger
import org.pottershouse.impactteam.sync.SyncPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OfflineTrackingScenarioTest {
    @Test
    fun tenDevicesConvergeDespiteDisconnectionReorderingDuplicatesAndClockSkew() {
        val nodes = (0..8).associate { index -> "member-$index" to Node("device-$index") }
        val leader = Node("leader")
        nodes.entries.forEachIndexed { index, (_, node) ->
            node.record(sequence = 1, at = now + index, latitude = index.toDouble())
            node.record(
                sequence = 2,
                at = if (index == 8) now + FUTURE_CLOCK_SKEW else now + 100 + index,
                latitude = index.toDouble() + 0.5,
            )
        }

        val relayB = nodes.getValue("member-1")
        val relayC = nodes.getValue("member-2")
        val disconnectedFromLeader = nodes.values.filter { it !== relayC }.map { it.id }.toSet()
        assertTrue(nodes.getValue("member-0").id in disconnectedFromLeader)

        var offeredRecords = 0
        listOf(8, 3, 6, 0, 7, 4, 5).forEachIndexed { deliveryIndex, memberIndex ->
            offeredRecords += exchange(
                from = nodes.getValue("member-$memberIndex"),
                to = relayB,
                at = now + 1_000 + deliveryIndex,
                reverseDelivery = deliveryIndex % 2 == 0,
            )
        }
        offeredRecords += exchange(relayB, relayC, now + 2_000, reverseDelivery = true)
        offeredRecords += exchange(relayC, leader, now + 3_000, reverseDelivery = true)

        val snapshot = leader.ledger.snapshot(now + 3_000)
        assertEquals(9, snapshot.size)
        assertEquals(
            (0..8).associate { index -> "member-$index" to 2L },
            snapshot.associate { it.memberId.value to it.observed.envelope.originSequence },
        )
        assertEquals(8.5, snapshot.single { it.memberId.value == "member-8" }.observed.envelope.payload.latitude)
        assertTrue(offeredRecords <= 54, "Anti-entropy offered $offeredRecords records")

        val memberZeroCurrent = snapshot.single { it.memberId.value == "member-0" }.observed
        assertIs<AcceptResult.Duplicate>(
            leader.ledger.acceptForwarded(memberZeroCurrent, now + 3_001, relayC.id),
        )
        val staleReplay = memberZeroCurrent.copy(
            envelope = memberZeroCurrent.envelope.copy(
                recordId = RecordId("member-0-stale-replay"),
                originSequence = 1,
            ),
        )
        assertIs<AcceptResult.StaleSequence>(
            leader.ledger.acceptForwarded(staleReplay, now + 3_002, relayC.id),
        )
        assertEquals(
            2,
            leader.ledger.snapshot(now + 3_002)
                .single { it.memberId.value == "member-0" }
                .observed.envelope.originSequence,
        )

        val convergedEcho = SyncPlanner(leader.ledger).plan(
            peerId = relayC.id,
            peerDigest = SyncPlanner(relayC.ledger).digest(now + 3_003),
            nowEpochMillis = now + 3_003,
        )
        assertTrue(convergedEcho.records.isEmpty())
    }

    private fun exchange(
        from: Node,
        to: Node,
        at: Long,
        reverseDelivery: Boolean = false,
    ): Int {
        val batch = SyncPlanner(from.ledger).plan(to.id, SyncPlanner(to.ledger).digest(at), at)
        val records = if (reverseDelivery) batch.records.reversed() else batch.records
        records.forEach { forwarded ->
            assertIs<AcceptResult.Accepted>(to.ledger.acceptForwarded(forwarded, at, from.id))
        }
        return records.size
    }

    private class Node(deviceId: String) {
        val id = DeviceId(deviceId)
        val ledger = TrackingLedger()

        fun record(sequence: Long, at: Long, latitude: Double) {
            assertIs<AcceptResult.Accepted>(
                ledger.accept(
                    envelope = envelope(sequence, at, latitude),
                    receivedAtEpochMillis = now + sequence,
                    arrivalPath = ArrivalPath.LOCAL,
                    suppliedByPeerId = null,
                ),
            )
        }

        private fun envelope(sequence: Long, at: Long, latitude: Double) = TrackingEnvelope(
            protocolVersion = 1,
            recordId = RecordId("${id.value}-$sequence"),
            tripId = TripId("proof-trip"),
            teamId = TeamId("blue"),
            memberId = MemberId(id.value.replace("device", "member")),
            originDeviceId = id,
            originSequence = sequence,
            createdAtEpochMillis = at,
            priority = MessagePriority.NORMAL,
            expiresAtEpochMillis = now + 3_600_000,
            payload = LocationPayload(
                latitude = latitude,
                longitude = 31.0335,
                capturedAtEpochMillis = at,
                accuracyMeters = 8.0,
                batteryPercent = 80,
            ),
        )
    }

    private companion object {
        const val now = 1_800_000_000_000L
        const val FUTURE_CLOCK_SKEW = 300_000L
    }
}
