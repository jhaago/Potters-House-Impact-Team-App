package org.pottershouse.impactteam.storage

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createImpactTeamDatabaseBuilder(): RoomDatabase.Builder<ImpactTeamDatabase> {
    val documentDirectory = checkNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    )
    val databasePath = checkNotNull(
        documentDirectory.URLByAppendingPathComponent(IMPACT_TEAM_DATABASE_NAME),
    ).path
    return Room.databaseBuilder<ImpactTeamDatabase>(name = checkNotNull(databasePath))
}
