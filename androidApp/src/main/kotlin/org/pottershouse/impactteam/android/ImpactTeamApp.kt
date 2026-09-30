package org.pottershouse.impactteam.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import org.pottershouse.impactteam.android.tracking.TrackingForegroundService
import org.pottershouse.impactteam.android.ui.ProofSetup
import org.pottershouse.impactteam.android.ui.TrackingProofScreen
import org.pottershouse.impactteam.android.ui.TrackingProofTelemetry
import org.pottershouse.impactteam.android.ui.TrackingProofUiState
import org.pottershouse.impactteam.android.ui.TrackingProofViewModel
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.storage.PersistentTrackingRepository
import org.pottershouse.impactteam.storage.buildImpactTeamDatabase
import org.pottershouse.impactteam.storage.createImpactTeamDatabaseBuilder
import java.util.UUID

@Composable
fun ImpactTeamApp() {
    val context = LocalContext.current.applicationContext
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val database = remember { buildImpactTeamDatabase(createImpactTeamDatabaseBuilder(context)) }
    val repository = remember { PersistentTrackingRepository(database.trackingRecordDao(), System::currentTimeMillis) }
    val deviceId = remember { proofDeviceId(context) }
    val viewModel = remember {
        TrackingProofViewModel(
            scope = scope,
            nowEpochMillis = System::currentTimeMillis,
            missingPermissions = { missingTrackingPermissions(context) },
            startTracking = { TrackingForegroundService.start(context, it) },
            stopTracking = { TrackingForegroundService.stop(context) },
            loadMembers = { tripId -> repository.loadLedger(tripId).snapshot(System.currentTimeMillis()) },
            telemetry = TrackingProofTelemetry.state,
        ).also { current ->
            current.updateSetup(
                ProofSetup(
                    tripId = "impact-proof",
                    tripName = "Impact Team Tracking Proof",
                    teamId = "blue",
                    memberId = "",
                    deviceId = deviceId,
                ),
            )
        }
    }
    val state by viewModel.state.collectAsState()

    val backgroundPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val foregroundPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    DisposableEffect(database) {
        onDispose { database.close() }
    }

    MaterialTheme {
        Surface {
            TrackingProofScreen(
                state = state,
                onSetupChanged = viewModel::updateSetup,
                onStart = viewModel::start,
                onStop = viewModel::stop,
                onRequestPermissions = {
                    val requested = foregroundPermissionNames(context)
                    if (requested.isNotEmpty()) {
                        foregroundPermissions.launch(requested.toTypedArray())
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
                onCopyDiagnostics = { clipboard.setText(AnnotatedString(it)) },
                onStartSeparationFieldTest = viewModel::startSeparationFieldTest,
                onResetSeparationFieldTest = viewModel::resetSeparationFieldTest,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImpactTeamAppPreview() {
    MaterialTheme {
        TrackingProofScreen(
            state = TrackingProofUiState(
                setup = ProofSetup(
                    tripId = "impact-proof",
                    tripName = "Zimbabwe Impact Team 2027",
                    teamId = "blue",
                    memberId = "amy",
                    deviceId = "device-preview",
                ),
            ),
            onSetupChanged = {},
            onStart = {},
            onStop = {},
            onRequestPermissions = {},
            onCopyDiagnostics = {},
        )
    }
}

private fun proofDeviceId(context: Context): String {
    val preferences = context.getSharedPreferences("tracking-proof", Context.MODE_PRIVATE)
    return preferences.getString("device-id", null) ?: "android-${UUID.randomUUID()}".also {
        preferences.edit().putString("device-id", it).apply()
    }
}

private fun missingTrackingPermissions(context: Context): Set<TrackingPermission> = buildSet {
    if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
        add(TrackingPermission.PRECISE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
        add(TrackingPermission.BACKGROUND_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
        add(TrackingPermission.NOTIFICATIONS)
    }
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ).any { !context.hasPermission(it) }
    ) {
        add(TrackingPermission.BLUETOOTH)
    }
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !context.hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
    ) {
        add(TrackingPermission.LOCAL_NETWORK)
    }
}

internal fun runtimePermissionNamesForSdk(sdkInt: Int): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (sdkInt >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}

private fun foregroundPermissionNames(context: Context): List<String> =
    runtimePermissionNamesForSdk(Build.VERSION.SDK_INT).filter { !context.hasPermission(it) }

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
