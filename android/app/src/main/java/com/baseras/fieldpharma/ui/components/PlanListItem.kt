package com.baseras.fieldpharma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

enum class PlanStopState { Done, Upcoming, Next, Missed }

data class PlanStop(
    val time: String,
    val name: String,
    val subtitle: String,
    val state: PlanStopState,
    val isChemist: Boolean = false,
)

@Composable
fun PlanListItem(
    stop: PlanStop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNext = stop.state == PlanStopState.Next
    val isDone = stop.state == PlanStopState.Done
    val isMissed = stop.state == PlanStopState.Missed

    val borderMod = if (isNext) {
        Modifier.border(
            1.5.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
            MaterialTheme.shapes.medium,
        )
    } else Modifier

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(borderMod)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = if (isNext) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isDone) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ─── Time rail ───
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    stop.time,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
            }

            // ─── Icon badge ───
            StopBadge(
                state = stop.state,
                icon = if (stop.isChemist) Icons.Default.LocalPharmacy else Icons.Default.LocalHospital,
            )

            Spacer(Modifier.width(12.dp))

            // ─── Name + subtitle ───
            Column(Modifier.weight(1f)) {
                Text(
                    stop.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    stop.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            // ─── Trailing state chip ───
            when (stop.state) {
                PlanStopState.Done ->
                    StatusPill("Done", PillTone.Success, icon = Icons.Default.CheckCircle)
                PlanStopState.Next ->
                    StatusPill("Next", PillTone.Accent)
                PlanStopState.Missed ->
                    StatusPill("Missed", PillTone.Error)
                PlanStopState.Upcoming -> {}
            }
        }
    }
}

@Composable
private fun StopBadge(state: PlanStopState, icon: ImageVector) {
    val bg = when (state) {
        PlanStopState.Done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        PlanStopState.Next -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        PlanStopState.Missed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        PlanStopState.Upcoming -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (state) {
        PlanStopState.Done -> MaterialTheme.colorScheme.primary
        PlanStopState.Next -> MaterialTheme.colorScheme.secondary
        PlanStopState.Missed -> MaterialTheme.colorScheme.error
        PlanStopState.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun PlanListPreview() {
    FieldPharmaTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanListItem(PlanStop("09:30", "Dr. R. Mehta", "Cardiology · Dadar", PlanStopState.Done), {})
            PlanListItem(PlanStop("10:30", "Apollo Pharmacy", "Chemist · Lower Parel", PlanStopState.Done, isChemist = true), {})
            PlanListItem(PlanStop("11:30", "Dr. R. Sharma", "Diabetology · Mahim · 2.3 km", PlanStopState.Next), {})
            PlanListItem(PlanStop("13:00", "Dr. S. Patel", "Pediatrics · Bandra · 4.1 km", PlanStopState.Upcoming), {})
            PlanListItem(PlanStop("08:30", "Dr. A. Iyer", "Oncology · Juhu", PlanStopState.Missed), {})
        }
    }
}
