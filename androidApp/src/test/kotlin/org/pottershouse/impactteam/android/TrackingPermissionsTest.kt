package org.pottershouse.impactteam.android

import android.Manifest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrackingPermissionsTest {
    @Test
    fun android13AndLaterRequestNearbyWifiAtRuntime() {
        assertFalse(Manifest.permission.NEARBY_WIFI_DEVICES in runtimePermissionNamesForSdk(32))
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in runtimePermissionNamesForSdk(33))
    }

    @Test
    fun nearbyWifiStatePermissionsRemainDeclaredOnModernAndroid() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        listOf(
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        ).forEach { permission ->
            val declaration = Regex(
                """<uses-permission[^>]*android:name=\"${Regex.escape(permission)}\"[^>]*/>""",
            ).find(manifest)?.value

            assertNotNull(declaration, "$permission must be declared for Nearby Connections")
            assertFalse(
                "android:maxSdkVersion" in declaration,
                "$permission must not be capped below modern Android versions",
            )
        }
    }
}
