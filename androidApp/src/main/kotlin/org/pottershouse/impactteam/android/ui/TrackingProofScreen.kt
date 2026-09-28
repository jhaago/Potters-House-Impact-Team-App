package org.pottershouse.impactteam.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pottershouse.impactteam.domain.TrackingHealth

@Composable
fun TrackingProofScreen(
    state: TrackingProofUiState,
    onSetupChanged: (ProofSetup) -> Unit,
    onStart: (ProofSetup) -> Unit,
    onStop: () -> Unit,
    onRequestPermissions: () -> Unit,
    onCopyDiagnostics: (String) -> Unit,
) {
    var confirmStart by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val isTracking = state.health is TrackingHealth.Active || state.health is TrackingHealth.Degraded

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("POTTER'S HOUSE IMPACT TEAM", style = MaterialTheme.typography.headlineSmall)
            Text("Offline tracking proof", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Tracking status: ${state.health.displayName()}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(state.health.detail())
                }
            }
        }
        item {
            ProofSetupScreen(state.setup, enabled = !isTracking, onSetupChanged = onSetupChanged)
            state.setupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.health is TrackingHealth.PermissionRequired) {
            item {
                Text(
                    "Permissions required: ${state.health.permissions.joinToString { it.name }}",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRequestPermissions) { Text("GRANT PERMISSIONS") }
            }
        }
        item {
            if (isTracking) {
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("STOP TRACKING")
                }
            } else {
                Button(onClick = { confirmStart = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("START TRACKING")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showDiagnostics = !showDiagnostics },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("DIAGNOSTICS")
            }
        }
        if (showDiagnostics) {
            item { DiagnosticsScreen(state.diagnostics, onCopyDiagnostics) }
        }
        if (state.members.isNotEmpty()) {
            item { Text("Recent team state", style = MaterialTheme.typography.titleLarge) }
            items(state.members, key = { it.memberId }) { member -> MemberStateRow(member) }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start tracking for this trip?") },
            text = { Text("Your location will be shared with nearby phones for this active proof trip.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStart = false
                    onStart(state.setup)
                }) { Text("CONFIRM START") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("CANCEL") }
            },
        )
    }
}

private fun TrackingHealth.displayName(): String = when (this) {
    TrackingHealth.Idle -> "Not started"
    is TrackingHealth.PermissionRequired -> "Permission required"
    is TrackingHealth.Active -> "Active"
    is TrackingHealth.Degraded -> "Degraded"
    is TrackingHealth.Stopped -> "Stopped"
}

private fun TrackingHealth.detail(): String = when (this) {
    TrackingHealth.Idle -> "Choose the shared trip details before starting."
    is TrackingHealth.PermissionRequired -> "Tracking has not started."
    is TrackingHealth.Active -> session.tripName
    is TrackingHealth.Degraded -> "${session.tripName} • ${issues.joinToString()}"
    is TrackingHealth.Stopped -> reason
}
