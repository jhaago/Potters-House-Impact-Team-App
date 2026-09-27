package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.MemberId

data class MemberTrackingState(
    val memberId: MemberId,
    val observed: ObservedEnvelope,
    val freshness: Freshness,
)
