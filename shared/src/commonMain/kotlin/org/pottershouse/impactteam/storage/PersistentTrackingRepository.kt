package org.pottershouse.impactteam.storage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.TripId
import org.pottershouse.impactteam.protocol.DecodeResult
import org.pottershouse.impactteam.protocol.EnvelopeCodec
import org.pottershouse.impactteam.protocol.EnvelopeValidator
import org.pottershouse.impactteam.protocol.ValidationResult
import org.pottershouse.impactteam.state.AcceptResult
import org.pottershouse.impactteam.state.ArrivalPath
import org.pottershouse.impactteam.state.ObservedEnvelope
import org.pottershouse.impactteam.state.TrackingLedger

data class CompactionResult(val expiredRecordsRemoved: Int)

class PersistentTrackingRepository(
    private val dao: TrackingRecordDao,
    private val nowEpochMillis: () -> Long,
) {
    private val mutex = Mutex()
    private var ledger = TrackingLedger()
    private val json = Json

    suspend fun accept(observed: ObservedEnvelope): AcceptResult = mutex.withLock {
        val envelope = observed.envelope
        when (val validation = EnvelopeValidator.validate(envelope, nowEpochMillis())) {
            is ValidationResult.Invalid -> return@withLock AcceptResult.RejectedInvalid(
                recordId = envelope.recordId,
                failures = validation.failures,
            )
            ValidationResult.Valid -> Unit
        }

        when (val result = dao.insertIfFresh(
            TrackingRecordEntity.from(
                observed = observed,
                canonicalEnvelopeJson = EnvelopeCodec.encode(envelope),
            ),
        )) {
            PersistenceInsertResult.Duplicate -> return@withLock AcceptResult.Duplicate(envelope.recordId)
            is PersistenceInsertResult.StaleSequence -> return@withLock AcceptResult.StaleSequence(
                observed = observed,
                highestAcceptedSequence = result.highestAcceptedSequence,
            )
            is PersistenceInsertResult.Inserted -> Unit
        }
        ledger.restore(observed)
    }

    suspend fun loadLedger(tripId: TripId): TrackingLedger = mutex.withLock {
        val restored = TrackingLedger()
        dao.recordsForTrip(tripId.value).forEach { row ->
            restoreRow(row, restored)
        }
        ledger = restored
        restored
    }

    suspend fun nextOriginSequence(deviceId: DeviceId): Long =
        dao.allocateNextOriginSequence(deviceId.value)

    suspend fun compact(nowEpochMillis: Long): CompactionResult =
        CompactionResult(expiredRecordsRemoved = dao.deleteExpired(nowEpochMillis))

    private suspend fun restoreRow(row: TrackingRecordEntity, restored: TrackingLedger) {
        val envelope = when (val decoded = EnvelopeCodec.decode(row.canonicalEnvelopeJson)) {
            is DecodeResult.Success -> decoded.envelope
            is DecodeResult.Malformed -> return quarantine(row, "Malformed envelope: ${decoded.reason}")
            is DecodeResult.TooLarge -> return quarantine(row, "Envelope too large: ${decoded.encodedBytes} bytes")
            is DecodeResult.UnsupportedProtocol -> return quarantine(
                row,
                "Unsupported protocol: ${decoded.protocolVersion}",
            )
        }

        when (val validation = EnvelopeValidator.validate(envelope, nowEpochMillis())) {
            is ValidationResult.Invalid -> return quarantine(
                row,
                "Invalid envelope: ${validation.failures.sortedBy { it.name }.joinToString()}",
            )
            ValidationResult.Valid -> Unit
        }

        if (!row.matches(envelope)) {
            return quarantine(row, "Persisted metadata does not match canonical envelope")
        }

        val observed = try {
            ObservedEnvelope(
                envelope = envelope,
                receivedAtEpochMillis = row.receivedAtEpochMillis,
                arrivalPath = ArrivalPath.valueOf(row.arrivalPath),
                suppliedByPeerId = row.suppliedByPeerId?.let(::DeviceId),
                relayCount = row.relayCount,
                recentPeerIds = json.decodeFromString<List<String>>(row.recentPeerIdsJson).map(::DeviceId),
            )
        } catch (error: IllegalArgumentException) {
            return quarantine(row, "Malformed receipt metadata: ${error.message ?: "invalid value"}")
        } catch (error: SerializationException) {
            return quarantine(row, "Malformed receipt metadata: ${error.message ?: "invalid JSON"}")
        }
        restored.restore(observed)
    }

    private suspend fun quarantine(row: TrackingRecordEntity, reason: String) {
        dao.quarantine(row.recordId, reason)
    }

    private fun TrackingRecordEntity.matches(
        envelope: org.pottershouse.impactteam.protocol.TrackingEnvelope,
    ): Boolean =
        recordId == envelope.recordId.value &&
            tripId == envelope.tripId.value &&
            originDeviceId == envelope.originDeviceId.value &&
            originSequence == envelope.originSequence &&
            expiresAtEpochMillis == envelope.expiresAtEpochMillis
}
