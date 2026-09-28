package org.pottershouse.impactteam.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomSequenceAllocationTest {
    @Test
    fun concurrentAllocationsAreUniqueAndMonotonic() = runTest {
        val database = Room.inMemoryDatabaseBuilder<ImpactTeamDatabase> {
            ImpactTeamDatabaseConstructor.initialize()
        }.setDriver(BundledSQLiteDriver()).build()

        try {
            val dao = database.trackingRecordDao()
            val allocated = coroutineScope {
                (1..20).map {
                    async { dao.allocateNextOriginSequence("device-1") }
                }.awaitAll()
            }

            assertEquals((1L..20L).toList(), allocated.sorted())
        } finally {
            database.close()
        }
    }
}
