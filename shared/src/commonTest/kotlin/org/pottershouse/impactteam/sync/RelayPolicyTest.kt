package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.protocol.locationEnvelope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayPolicyTest {
    private val now = 1_700_000_100_000L
    private val peer = DeviceId("peer-b")
    private val policy = RelayPolicy(maxRelayCount = 8)

    @Test
    fun duplicateAndImmediateEchoAreSuppressed() {
        val observed = observation(suppliedBy = peer)

        assertFalse(policy.canOffer(observed, peer, now))
        assertFalse(policy.canOffer(observed.copy(suppliedByPeerId = null, recentPeerIds = listOf(peer)), peer, now))
    }

    @Test
    fun maximumRelayCountIsEight() {
        assertTrue(policy.canOffer(observation(relayCount = 7), peer, now))
        assertFalse(policy.canOffer(observation(relayCount = 8), peer, now))
    }

    @Test
    fun expiredRecordsAreRejected() {
        val expired = observation().copy(
            envelope = locationEnvelope().copy(expiresAtEpochMillis = now),
        )

        assertFalse(policy.canOffer(expired, peer, now))
    }

    private fun observation(
        suppliedBy: DeviceId? = null,
        relayCount: Int = 0,
    ) = ObservedEnvelope(
        envelope = locationEnvelope(),
        receivedAtEpochMillis = now,
        arrivalPath = ArrivalPath.RELAYED,
        suppliedByPeerId = suppliedBy,
        relayCount = relayCount,
    )
}
