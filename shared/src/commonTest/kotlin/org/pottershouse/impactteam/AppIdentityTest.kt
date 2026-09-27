package org.pottershouse.impactteam

import kotlin.test.Test
import kotlin.test.assertEquals

class AppIdentityTest {
    @Test
    fun defaultIdentityIsStable() {
        val identity = AppIdentity.default

        assertEquals("Potter's House Impact Team", identity.applicationName)
        assertEquals(1, identity.protocolVersion)
    }

    @Test
    fun defaultApplicationNameIsExportedForNativeShells() {
        assertEquals(
            AppIdentity.default.applicationName,
            defaultApplicationName(),
        )
    }
}
