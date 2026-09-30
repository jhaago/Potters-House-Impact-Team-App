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
    onStartSeparationFieldTest: (String) -> Unit = {},
    onResetSeparationFieldTest: () -> Unit = {},
) {
    var confirmStart by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var selectedFieldTestMemberId by remember(state.setup.tripId, state.setup.teamId) {
        mutableStateOf<String?>(null)
    }
    val isTracking = state.health is TrackingHealth.Active || state.health is TrackingHealth.Degraded
    val fieldTestMembers = state.members.filter { it.memberId != state.setup.memberId }
    val selectedFieldTestMember = selectedFieldTestMemberId?.takeIf { selected ->
        fieldTestMembers.any { it.memberId == selected }
    } ?: fieldTestMembers.singleOrNull()?.memberId

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
        if (isTracking) {
            item {
                SeparationFieldTestCard(
                    fieldTest = state.separationFieldTest,
                    members = fieldTestMembers,
                    selectedMemberId = selectedFieldTestMember,
                    onMemberSelected = { selectedFieldTestMemberId = it },
                    onStart = { selectedFieldTestMember?.let(onStartSeparationFieldTest) },
                    onReset = onResetSeparationFieldTest,
                    onCopy = { onCopyDiagnostics(state.separationFieldTest.redactedSummary()) },
                )
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

@Composable
private fun SeparationFieldTestCard(
    fieldTest: SeparationFieldTestState,
    members: List<MemberStateRowModel>,
    selectedMemberId: String?,
    onMemberSelected: (String) -> Unit,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Separation field test", style = MaterialTheme.typography.titleLarge)
            Text(
                "Proof-only helper. Start together, hold near 200 m for WARNING, move beyond 275 m for SERIOUS, then return inside 140 m until recovered.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!fieldTest.isActive) {
                if (members.isEmpty()) {
                    Text("Waiting for another team member to appear.")
                } else {
                    Text("Choose the teammate who will move away from the group.")
                    members.forEach { member ->
                        OutlinedButton(
                            onClick = { onMemberSelected(member.memberId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (member.memberId == selectedMemberId) {
                                    "Selected: ${member.memberId}"
                                } else {
                                    member.memberId
                                },
                            )
                        }
                    }
                    Button(
                        onClick = onStart,
                        enabled = selectedMemberId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("START FIELD TEST")
                    }
                }
            } else {
                Text("Testing ${fieldTest.memberId}", style = MaterialTheme.typography.titleMedium)
                Text("Within range: ${fieldTest.withinRangeAtEpochMillis.milestoneStatus()}")
                Text("Watching: ${fieldTest.watchingAtEpochMillis.milestoneStatus()}")
                Text("WARNING: ${fieldTest.warningAtEpochMillis.milestoneStatus()}")
                Text("SERIOUS: ${fieldTest.seriousAtEpochMillis.milestoneStatus()}")
                Text("Recovered: ${fieldTest.recoveredAtEpochMillis.milestoneStatus()}")
                Text(
                    if (fieldTest.isComplete) "Field test complete" else "Field test in progress",
                    color = if (fieldTest.isComplete) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("COPY FIELD TEST RESULT")
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("RESET FIELD TEST")
                }
            }
        }
    }
}

private fun Long?.milestoneStatus(): String = if (this == null) "pending" else "observed"

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
