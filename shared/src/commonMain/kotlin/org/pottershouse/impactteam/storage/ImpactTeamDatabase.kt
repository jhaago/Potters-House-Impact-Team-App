package org.pottershouse.impactteam.storage

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [TrackingRecordEntity::class, OriginSequenceEntity::class, OriginHighWaterEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ImpactTeamDatabaseConstructor::class)
abstract class ImpactTeamDatabase : RoomDatabase() {
    abstract fun trackingRecordDao(): TrackingRecordDao
}

@Suppress("KotlinNoActualForExpect")
expect object ImpactTeamDatabaseConstructor : RoomDatabaseConstructor<ImpactTeamDatabase> {
    override fun initialize(): ImpactTeamDatabase
}
