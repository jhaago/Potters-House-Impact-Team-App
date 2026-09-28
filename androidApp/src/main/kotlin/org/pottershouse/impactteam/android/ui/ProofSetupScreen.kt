package org.pottershouse.impactteam.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProofSetupScreen(
    setup: ProofSetup,
    enabled: Boolean,
    onSetupChanged: (ProofSetup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = setup.tripName,
            onValueChange = { onSetupChanged(setup.copy(tripName = it)) },
            label = { androidx.compose.material3.Text("Trip name") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setup.tripId,
            onValueChange = { onSetupChanged(setup.copy(tripId = it)) },
            label = { androidx.compose.material3.Text("Shared trip ID") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setup.teamId,
            onValueChange = { onSetupChanged(setup.copy(teamId = it)) },
            label = { androidx.compose.material3.Text("Team ID") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setup.memberId,
            onValueChange = { onSetupChanged(setup.copy(memberId = it)) },
            label = { androidx.compose.material3.Text("Your member ID or name") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setup.deviceId,
            onValueChange = {},
            label = { androidx.compose.material3.Text("This install's device ID") },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
