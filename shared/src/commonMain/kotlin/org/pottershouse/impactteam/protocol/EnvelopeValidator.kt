package org.pottershouse.impactteam.protocol

enum class ValidationFailure {
    UnsupportedProtocol,
    BlankIdentifier,
    NonPositiveSequence,
    LatitudeOutOfRange,
    LongitudeOutOfRange,
    NegativeAccuracy,
    BatteryOutOfRange,
    Expired,
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val failures: Set<ValidationFailure>) : ValidationResult
}

object EnvelopeValidator {
    fun validate(envelope: TrackingEnvelope, nowEpochMillis: Long): ValidationResult {
        val failures = buildSet {
            if (envelope.protocolVersion != EnvelopeCodec.SUPPORTED_PROTOCOL_VERSION) {
                add(ValidationFailure.UnsupportedProtocol)
            }
            if (
                envelope.recordId.value.isBlank() ||
                envelope.tripId.value.isBlank() ||
                envelope.teamId.value.isBlank() ||
                envelope.memberId.value.isBlank() ||
                envelope.originDeviceId.value.isBlank()
            ) {
                add(ValidationFailure.BlankIdentifier)
            }
            if (envelope.originSequence <= 0) add(ValidationFailure.NonPositiveSequence)
            if (envelope.payload.latitude !in -90.0..90.0) add(ValidationFailure.LatitudeOutOfRange)
            if (envelope.payload.longitude !in -180.0..180.0) add(ValidationFailure.LongitudeOutOfRange)
            if (!envelope.payload.accuracyMeters.isFinite() || envelope.payload.accuracyMeters < 0.0) {
                add(ValidationFailure.NegativeAccuracy)
            }
            if (envelope.payload.batteryPercent !in 0..100) add(ValidationFailure.BatteryOutOfRange)
            if (envelope.expiresAtEpochMillis <= nowEpochMillis) add(ValidationFailure.Expired)
        }

        return if (failures.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(failures)
    }
}
