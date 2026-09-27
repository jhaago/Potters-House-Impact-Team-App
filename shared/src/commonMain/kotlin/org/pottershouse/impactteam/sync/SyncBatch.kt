package org.pottershouse.impactteam.sync

import org.pottershouse.impactteam.state.ObservedEnvelope

data class SyncBatch(
    val records: List<ObservedEnvelope>,
    val encodedBytes: Int,
)
