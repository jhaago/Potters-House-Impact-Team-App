package org.pottershouse.impactteam.android

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingPermissionsTest {
    @Test
    fun android13AndLaterRequestNearbyWifiAtRuntime() {
        assertFalse(Manifest.permission.NEARBY_WIFI_DEVICES in runtimePermissionNamesForSdk(32))
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in runtimePermissionNamesForSdk(33))
    }
}
