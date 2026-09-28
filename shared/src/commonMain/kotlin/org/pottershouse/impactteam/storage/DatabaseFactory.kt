package org.pottershouse.impactteam.storage

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

const val IMPACT_TEAM_DATABASE_NAME = "impact-team.db"

fun buildImpactTeamDatabase(
    builder: RoomDatabase.Builder<ImpactTeamDatabase>,
): ImpactTeamDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .build()
