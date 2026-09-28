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

@Composable
fun MemberStateRow(row: MemberStateRowModel) {
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
        Text("${row.arrivalLabel} • ${row.batteryLabel} • ${row.accuracyLabel}")
    }
}
