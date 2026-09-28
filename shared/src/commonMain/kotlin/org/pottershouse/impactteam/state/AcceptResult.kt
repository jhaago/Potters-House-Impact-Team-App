package org.pottershouse.impactteam.state

import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.ValidationFailure

sealed interface AcceptResult {
    data class Accepted(
        val observed: ObservedEnvelope,
        val replacedPrevious: Boolean,
    ) : AcceptResult

    data class Duplicate(val recordId: RecordId) : AcceptResult

    data class StaleSequence(
        val observed: ObservedEnvelope,
        val highestAcceptedSequence: Long,
    ) : AcceptResult

    data class RejectedInvalid(
        val recordId: RecordId,
        val failures: Set<ValidationFailure>,
    ) : AcceptResult
}
