package org.pottershouse.impactteam.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TeamRole {
    @SerialName("member")
    MEMBER,

    @SerialName("team_leader")
    TEAM_LEADER,

    @SerialName("trip_leader")
    TRIP_LEADER,
}
