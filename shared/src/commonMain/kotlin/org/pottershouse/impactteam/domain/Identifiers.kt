package org.pottershouse.impactteam.domain

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TripId(val value: String)

@Serializable
@JvmInline
value class TeamId(val value: String)

@Serializable
@JvmInline
value class MemberId(val value: String)

@Serializable
@JvmInline
value class DeviceId(val value: String)

@Serializable
@JvmInline
value class RecordId(val value: String)
