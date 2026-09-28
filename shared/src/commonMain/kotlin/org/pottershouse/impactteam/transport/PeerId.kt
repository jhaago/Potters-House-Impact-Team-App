package org.pottershouse.impactteam.transport

import kotlinx.serialization.Serializable
import org.pottershouse.impactteam.domain.DeviceId
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class PeerId(val deviceId: DeviceId)
