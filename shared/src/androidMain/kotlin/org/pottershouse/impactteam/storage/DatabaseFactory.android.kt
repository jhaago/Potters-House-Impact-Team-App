package org.pottershouse.impactteam.storage

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

fun createImpactTeamDatabaseBuilder(
    context: Context,
): RoomDatabase.Builder<ImpactTeamDatabase> {
    val databasePath = context.applicationContext.getDatabasePath(IMPACT_TEAM_DATABASE_NAME)
    return Room.databaseBuilder<ImpactTeamDatabase>(
        context = context.applicationContext,
        name = databasePath.absolutePath,
    )
}
