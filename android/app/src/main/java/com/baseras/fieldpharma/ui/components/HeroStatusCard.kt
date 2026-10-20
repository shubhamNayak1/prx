package com.baseras.fieldpharma.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.*

sealed interface AttendanceState {
    data object NotPunched : AttendanceState
    data class PunchedIn(val timeLabel: String) : AttendanceState
    data class PunchedOut(val timeLabel: String) : AttendanceState
}

data class NextStop(
    val name: String,
    val distanceKm: Double?,
    val etaLabel: String?,
)

/**
 * Full-width hero on Home. Shows:
 *  - a glanceable attendance state in the top strip
 *  - today's call progress bar + fraction
 *  - the next stop with a CTA (Punch in / Check in / Day complete)
 */
@Composable
fun HeroStatusCard(
    greeting: String,
    attendance: AttendanceState,
    callsDone: Int,
    callsPlanned: Int,
    nextStop: NextStop?,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box {
            // Subtle brand gradient wash on the top strip
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to primary.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        )
                    )
            )
            Column(Modifier.padding(20.dp)) {
                // ─── top row: greeting + attendance pill ───
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.titleMedium,
                        color = subtle,
                        modifier = Modifier.weight(1f),
                    )
                    AttendancePill(attendance)
                }

                Spacer(Modifier.height(8.dp))

                // ─── progress ───
                val target = if (callsPlanned > 0) callsDone / callsPlanned.toFloat() else 0f
                val progress by animateFloatAsState(target.coerceIn(0f, 1f), label = "progress")

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$callsDone",
                        style = MaterialTheme.typography.displayMedium,
                        color = onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = " / $callsPlanned calls",
                        style = MaterialTheme.typography.titleMedium,
                        color = subtle,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                    color = primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )

                Spacer(Modifier.height(16.dp))

                // ─── next stop / primary action ───
                NextStopRow(
                    attendance = attendance,
                    nextStop = nextStop,
                    callsDone = callsDone,
                    callsPlanned = callsPlanned,
                    onPrimaryAction = onPrimaryAction,
                )
            }
        }
    }
}

@Composable
private fun AttendancePill(state: AttendanceState) {
    when (state) {
        AttendanceState.NotPunched -> StatusPill("Not punched in", PillTone.Warn)
        is AttendanceState.PunchedIn -> StatusPill("In · ${state.timeLabel}", PillTone.Success, icon = Icons.Default.CheckCircle)
        is AttendanceState.PunchedOut -> StatusPill("Out · ${state.timeLabel}", PillTone.Neutral)
    }
}

@Composable
private fun NextStopRow(
    attendance: AttendanceState,
    nextStop: NextStop?,
    callsDone: Int,
    callsPlanned: Int,
    onPrimaryAction: () -> Unit,
) {
    val isPunchedIn = attendance is AttendanceState.PunchedIn

    // Decide label + CTA based on day state
    val (title, subtitle, cta) = when {
        !isPunchedIn -> Triple(
            "Start your day",
            "Mark attendance to open your plan",
            "Punch in",
        )
        callsPlanned == 0 -> Triple(
            "No calls planned",
            "Add stops from Plan",
            "Open Plan",
        )
        callsDone >= callsPlanned -> Triple(
            "All calls done 🎉",
            "Review your DCR and submit",
            "Review DCR",
        )
        nextStop == null -> Triple(
            "Pick next stop",
            "Choose any client to check in",
            "Pick client",
        )
        else -> Triple(
            "Next · ${nextStop.name}",
            buildString {
                if (nextStop.distanceKm != null) append("%.1f km".format(nextStop.distanceKm))
                if (nextStop.distanceKm != null && nextStop.etaLabel != null) append("  ·  ")
                if (nextStop.etaLabel != null) append(nextStop.etaLabel)
            }.ifBlank { "Tap to check in" },
            "Check in",
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        FilledTonalButton(
            onClick = onPrimaryAction,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(
                if (!isPunchedIn) Icons.Default.PlayArrow else Icons.Default.ArrowForward,
                null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(cta, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, name = "Punched in — mid day")
@Composable
private fun HeroPreviewMidDay() {
    FieldPharmaTheme {
        Box(Modifier.padding(16.dp)) {
            HeroStatusCard(
                greeting = "Good morning, Amit",
                attendance = AttendanceState.PunchedIn("09:12"),
                callsDone = 4,
                callsPlanned = 12,
                nextStop = NextStop("Dr. R. Sharma", distanceKm = 2.3, etaLabel = "8 min"),
                onPrimaryAction = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, name = "Not punched")
@Composable
private fun HeroPreviewNotPunched() {
    FieldPharmaTheme {
        Box(Modifier.padding(16.dp)) {
            HeroStatusCard(
                greeting = "Good morning, Amit",
                attendance = AttendanceState.NotPunched,
                callsDone = 0,
                callsPlanned = 12,
                nextStop = null,
                onPrimaryAction = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, name = "Day complete")
@Composable
private fun HeroPreviewComplete() {
    FieldPharmaTheme {
        Box(Modifier.padding(16.dp)) {
            HeroStatusCard(
                greeting = "Good evening, Amit",
                attendance = AttendanceState.PunchedIn("09:12"),
                callsDone = 12,
                callsPlanned = 12,
                nextStop = null,
                onPrimaryAction = {},
            )
        }
    }
}
