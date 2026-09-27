package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.TrackingLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeshScenarioTest {
    private val now = 1_700_000_100_000L

    @Test
    fun newestRecordPropagatesAcrossFourDevicesWithBoundedOffers() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val leader = Node("leader")
        a.ledger.accept(envelope(1, "a"), now, ArrivalPath.LOCAL, null)
        a.ledger.accept(envelope(2, "a"), now + 1, ArrivalPath.LOCAL, null)

        var offers = 0
        offers += exchange(a, b, now + 2)
        offers += exchange(b, c, now + 3)
        offers += exchange(c, leader, now + 4)

        assertEquals(2, leader.ledger.snapshot(now + 4).single().observed.envelope.originSequence)
        assertEquals(2, leader.ledger.syncCandidates(now + 4).map { it.envelope.recordId }.toSet().size)
        assertTrue(offers <= 6)

        val immediateEcho = SyncPlanner(b.ledger).plan(a.id, SyncPlanner(a.ledger).digest(now + 5), now + 5)
        assertTrue(immediateEcho.records.isEmpty())
    }

    private fun exchange(from: Node, to: Node, at: Long): Int {
        val batch = SyncPlanner(from.ledger).plan(to.id, SyncPlanner(to.ledger).digest(at), at)
        batch.records.forEach { forwarded ->
            assertIs<AcceptResult.Accepted>(to.ledger.acceptForwarded(forwarded, at, from.id))
        }
        return batch.records.size
    }

    private data class Node(val value: String) {
        val id = DeviceId(value)
        val ledger = TrackingLedger()
    }
}
