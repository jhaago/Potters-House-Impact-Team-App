package org.pottershouse.impactteam.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

sealed interface PersistenceInsertResult {
    data class Inserted(val rowId: Long) : PersistenceInsertResult
    data object Duplicate : PersistenceInsertResult
    data class StaleSequence(val highestAcceptedSequence: Long) : PersistenceInsertResult
}

@Dao
interface TrackingRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: TrackingRecordEntity): Long

    @Query(
        "SELECT * FROM tracking_records " +
            "WHERE trip_id = :tripId AND quarantine_reason IS NULL " +
            "ORDER BY origin_device_id, origin_sequence",
    )
    suspend fun recordsForTrip(tripId: String): List<TrackingRecordEntity>

    @Query(
        "SELECT COUNT(*) FROM tracking_records " +
            "WHERE record_id = :recordId AND quarantine_reason IS NULL",
    )
    suspend fun activeRecordCount(recordId: String): Int

    @Query(
        "SELECT highest_accepted_sequence FROM origin_high_water WHERE device_id = :deviceId",
    )
    suspend fun acceptedOriginHighWater(deviceId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initializeOriginHighWater(highWater: OriginHighWaterEntity): Long

    @Query(
        "UPDATE origin_high_water SET highest_accepted_sequence = :sequence " +
            "WHERE device_id = :deviceId AND highest_accepted_sequence < :sequence",
    )
    suspend fun raiseOriginHighWater(deviceId: String, sequence: Long): Int

    @Query(
        "DELETE FROM tracking_records WHERE quarantine_reason IS NOT NULL AND " +
            "(record_id = :recordId OR " +
            "(origin_device_id = :deviceId AND origin_sequence = :originSequence))",
    )
    suspend fun deleteQuarantinedConflicts(
        recordId: String,
        deviceId: String,
        originSequence: Long,
    ): Int

    @Transaction
    suspend fun insertIfFresh(record: TrackingRecordEntity): PersistenceInsertResult {
        if (activeRecordCount(record.recordId) > 0) return PersistenceInsertResult.Duplicate
        val repairedQuarantinedConflict = deleteQuarantinedConflicts(
            record.recordId,
            record.originDeviceId,
            record.originSequence,
        ) > 0

        val highestSequence = acceptedOriginHighWater(record.originDeviceId)
        if (
            highestSequence != null &&
            (record.originSequence < highestSequence ||
                (record.originSequence == highestSequence && !repairedQuarantinedConflict))
        ) {
            return PersistenceInsertResult.StaleSequence(highestSequence)
        }

        val rowId = insertRecord(record)
        return if (rowId == -1L) {
            PersistenceInsertResult.Duplicate
        } else {
            initializeOriginHighWater(
                OriginHighWaterEntity(record.originDeviceId, record.originSequence),
            )
            raiseOriginHighWater(record.originDeviceId, record.originSequence)
            PersistenceInsertResult.Inserted(rowId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initializeOriginSequence(sequence: OriginSequenceEntity): Long

    @Query("UPDATE origin_sequences SET last_sequence = last_sequence + 1 WHERE device_id = :deviceId")
    suspend fun incrementOriginSequence(deviceId: String): Int

    @Query("SELECT last_sequence FROM origin_sequences WHERE device_id = :deviceId")
    suspend fun currentOriginSequence(deviceId: String): Long?

    @Transaction
    suspend fun allocateNextOriginSequence(deviceId: String): Long {
        initializeOriginSequence(OriginSequenceEntity(deviceId, 0))
        check(incrementOriginSequence(deviceId) == 1) { "Unable to allocate sequence for $deviceId" }
        return checkNotNull(currentOriginSequence(deviceId))
    }

    @Query("DELETE FROM tracking_records WHERE expires_at_epoch_millis <= :nowEpochMillis")
    suspend fun deleteExpired(nowEpochMillis: Long): Int

    @Query("UPDATE tracking_records SET quarantine_reason = :reason WHERE record_id = :recordId")
    suspend fun quarantine(recordId: String, reason: String): Int
}
