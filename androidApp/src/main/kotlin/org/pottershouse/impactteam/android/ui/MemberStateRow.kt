package org.pottershouse.impactteam.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pottershouse.impactteam.state.SeparationLevel

@Composable
fun MemberStateRow(row: MemberStateRowModel) {
    val isSeparationAlert = row.separationLevel == SeparationLevel.WARNING ||
        row.separationLevel == SeparationLevel.SERIOUS

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.memberId, style = MaterialTheme.typography.titleMedium)
            Text(row.ageLabel)
        }
        Text(
            row.freshnessLabel,
            color = if (row.isLastKnown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            row.separationLabel(),
            color = if (isSeparationAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text("${row.arrivalLabel} • ${row.batteryLabel} • ${row.accuracyLabel}")
    }
}

private fun MemberStateRowModel.separationLabel(): String {
    val distance = separationDistanceMeters?.let { " • $it m from group" }.orEmpty()
    val movingAway = if (separationMovingAway) " • moving away" else ""
    return when (separationLevel) {
        SeparationLevel.SERIOUS -> "Separation: SERIOUS$distance$movingAway"
        SeparationLevel.WARNING -> "Separation: WARNING$distance$movingAway"
        SeparationLevel.CLEAR -> when (rawSeparationLevel) {
            SeparationLevel.WARNING,
            SeparationLevel.SERIOUS,
            -> "Separation: Watching$distance$movingAway"
            SeparationLevel.CLEAR -> "Separation: Within range"
            SeparationLevel.INSUFFICIENT_DATA -> "Separation: Unknown"
        }
        SeparationLevel.INSUFFICIENT_DATA -> "Separation: Unknown"
    }
}
