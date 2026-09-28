package org.pottershouse.impactteam.sync

import kotlinx.serialization.Serializable
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId

@Serializable
data class SyncDigest(
    val highestSequenceByOrigin: Map<DeviceId, Long>,
    val importantRecordIds: Set<RecordId>,
) {
    companion object {
        val EMPTY = SyncDigest(emptyMap(), emptySet())
    }
}
