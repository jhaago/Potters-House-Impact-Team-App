package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId

data class SyncDigest(
    val highestSequenceByOrigin: Map<DeviceId, Long>,
    val importantRecordIds: Set<RecordId>,
) {
    companion object {
        val EMPTY = SyncDigest(emptyMap(), emptySet())
    }
}
