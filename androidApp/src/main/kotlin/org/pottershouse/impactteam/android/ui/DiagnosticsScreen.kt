package org.pottershouse.impactteam.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsScreen(
    diagnostics: DiagnosticsUiState,
    onCopyDiagnostics: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Redacted diagnostics", style = MaterialTheme.typography.titleLarge)
            Text("Nearby phones: ${diagnostics.nearbyCount}")
            Text("Last exchange: ${diagnostics.lastExchangeAge}")
            Text("Accepted: ${diagnostics.acceptedCount}")
            Text("Duplicates: ${diagnostics.duplicateCount}")
            Text("Rejected: ${diagnostics.rejectedCount}")
            Text("Battery sample: ${diagnostics.batteryPercent?.let { "$it%" } ?: "Unknown"}")
            diagnostics.authenticationDigits.forEach { auth ->
                Text(
                    "Compare Nearby code ${auth.digits} with ${auth.peerId}",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            diagnostics.failures.forEach { failure ->
                Text("Transport failure: $failure", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { onCopyDiagnostics(diagnostics.redactedText()) }) {
                Text("COPY REDACTED DIAGNOSTICS")
            }
        }
    }
}
