package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baseras.fieldpharma.data.local.AttendanceEntity
import com.baseras.fieldpharma.ui.components.InlineSpinner
import com.baseras.fieldpharma.ui.components.PillTone
import com.baseras.fieldpharma.ui.components.StatusPill
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Punch-in / punch-out dialog with multi-session support.
 *
 * - sessions is empty → single "Punch in now" CTA
 * - latest session has no punchOutAt (open) → shows sessions list + "Punch out now"
 * - latest session is closed → shows sessions list + "Punch in again" CTA
 * - total time at the bottom sums all closed sessions + live hours for the open one
 */
@Composable
fun PunchDialog(
    sessions: List<AttendanceEntity>,
    busy: Boolean,
    onPunchIn: () -> Unit,
    onPunchOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val latest = sessions.lastOrNull()                          // list is oldest-first
    val hasOpen = latest?.punchOutAt == null && latest != null
    val now = remember { Instant.now() }
    val today = LocalDate.now()
    val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !busy),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {

                // ─── header ───
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                sessions.isEmpty() -> "Punch in"
                                hasOpen -> "Punch out"
                                else -> "Punch in again"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                today.format(dateFmt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        sessions.isEmpty() -> StatusPill("Pending", PillTone.Warn)
                        hasOpen -> StatusPill("Working", PillTone.Info)
                        else -> StatusPill("Out", PillTone.Neutral)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // ─── session list OR primer for a fresh day ───
                if (sessions.isEmpty()) {
                    PunchPreviewRow(label = "Punch in time", timeIso = now.toString(), isPlanned = true)
                } else {
                    val maxH = (sessions.size * 56).coerceAtMost(220)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxH.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sessions, key = { it.localId }) { s ->
                            SessionRow(session = s)
                        }
                    }
                    if (hasOpen) {
                        Spacer(Modifier.height(10.dp))
                        PunchPreviewRow(label = "Punch out time", timeIso = now.toString(), isPlanned = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    TotalRow(sessions, previewEndIso = if (hasOpen) now.toString() else null)
                }

                Spacer(Modifier.height(16.dp))

                // ─── action ───
                when {
                    hasOpen -> Button(
                        onClick = onPunchOut,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        if (busy) InlineSpinner(color = MaterialTheme.colorScheme.onError)
                        else {
                            Icon(Icons.Default.Logout, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Punch out now", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> Button(
                        onClick = onPunchIn,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        if (busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
                        else {
                            Icon(if (sessions.isEmpty()) Icons.Default.PlayArrow else Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (sessions.isEmpty()) "Punch in now" else "Punch in again",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Dismiss", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/* ─────── pieces ─────── */

@Composable
private fun SessionRow(session: AttendanceEntity) {
    val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    val inTime = runCatching { fmt.format(Instant.parse(session.punchInAt)) }.getOrNull() ?: "—"
    val outTime = session.punchOutAt?.let { runCatching { fmt.format(Instant.parse(it)) }.getOrNull() }
    val isOpen = session.punchOutAt == null
    val dur = session.punchOutAt?.let { durationLabel(session.punchInAt, it) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(
                        if (isOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Schedule, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(inTime)
                        append(" → ")
                        append(outTime ?: "ongoing")
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dur != null) {
                        Text(dur, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                    GpsBadge(session.punchInLat, session.punchInLng)
                }
            }
            if (isOpen) StatusPill("Live", PillTone.Info)
        }
    }
}

/** Compact "📍 captured" indicator; shows actual coords on tap in future. */
@Composable
private fun GpsBadge(lat: Double?, lng: Double?) {
    if (lat == null || lng == null) {
        Text(
            "📍 No GPS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Text(
        "📍 ${"%.4f".format(lat)}, ${"%.4f".format(lng)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PunchPreviewRow(label: String, timeIso: String?, isPlanned: Boolean) {
    val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    val time = timeIso?.let { runCatching { fmt.format(Instant.parse(it)) }.getOrNull() } ?: "—"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                time + if (isPlanned) "  (now)" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlanned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TotalRow(sessions: List<AttendanceEntity>, previewEndIso: String?) {
    val totalMs = sessions.sumOf { s ->
        val end = s.punchOutAt ?: previewEndIso
        if (end == null) 0L
        else runCatching {
            Duration.between(Instant.parse(s.punchInAt), Instant.parse(end)).toMillis().coerceAtLeast(0L)
        }.getOrDefault(0L)
    }
    val hours = totalMs / 3_600_000
    val mins = (totalMs / 60_000) % 60

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Total hours",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${hours}h ${mins}m",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun durationLabel(startIso: String, endIso: String): String? {
    val d = runCatching { Duration.between(Instant.parse(startIso), Instant.parse(endIso)) }.getOrNull() ?: return null
    if (d.isNegative) return null
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return "${h}h ${m}m"
}

@Preview
@Composable
private fun PunchDialogEmpty() {
    FieldPharmaTheme { PunchDialog(sessions = emptyList(), busy = false, onPunchIn = {}, onPunchOut = {}, onDismiss = {}) }
}
