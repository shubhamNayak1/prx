package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Full-screen-ish dialog for marking attendance. Dialog shape per feedback:
 * - today's date is auto-filled on open
 * - time field is auto-filled with "now"
 * - Punch-out row stays hidden until Punch-in has happened
 * - after Punch-out, dialog displays total hours worked
 */
@Composable
fun PunchDialog(
    attendance: AttendanceEntity?,
    busy: Boolean,
    onPunchIn: () -> Unit,
    onPunchOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val punchedIn = attendance?.punchInAt != null
    val punchedOut = attendance?.punchOutAt != null
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
                            if (punchedOut) "Day summary"
                            else if (punchedIn) "Punch out"
                            else "Punch in",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                null,
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
                        punchedOut -> StatusPill("Done", PillTone.Success)
                        punchedIn -> StatusPill("Working", PillTone.Info)
                        else -> StatusPill("Pending", PillTone.Warn)
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                // ─── punch IN row (always shown once something has happened; shows "will record now" before anything) ───
                PunchRow(
                    label = "Punched in",
                    timeIso = attendance?.punchInAt ?: (if (!punchedIn) now.toString() else null),
                    isPlanned = !punchedIn,
                )

                // ─── punch OUT row (only after punch-in) ───
                if (punchedIn) {
                    Spacer(Modifier.height(10.dp))
                    PunchRow(
                        label = "Punched out",
                        timeIso = attendance?.punchOutAt ?: (if (!punchedOut) now.toString() else null),
                        isPlanned = !punchedOut,
                    )

                    // Hours worked (shown once both are present OR a punch-in with "now" preview)
                    val startIso = attendance?.punchInAt
                    val endIso = attendance?.punchOutAt ?: if (!punchedOut) now.toString() else null
                    if (startIso != null && endIso != null) {
                        Spacer(Modifier.height(10.dp))
                        HoursWorkedRow(startIso, endIso, previewOnly = !punchedOut)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ─── action ───
                when {
                    punchedOut -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Close", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    punchedIn -> {
                        Button(
                            onClick = onPunchOut,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            if (busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Punch out now", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onPunchIn,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            if (busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Punch in now", style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun PunchRow(label: String, timeIso: String?, isPlanned: Boolean) {
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    val time = timeIso?.let { runCatching { timeFmt.format(Instant.parse(it)) }.getOrNull() } ?: "—"
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Schedule,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
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
private fun HoursWorkedRow(startIso: String, endIso: String, previewOnly: Boolean) {
    val start = runCatching { Instant.parse(startIso) }.getOrNull() ?: return
    val end = runCatching { Instant.parse(endIso) }.getOrNull() ?: return
    if (!end.isAfter(start)) return
    val d = Duration.between(start, end)
    val hours = d.toHours()
    val mins = d.toMinutes() % 60
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.LocationOn,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (previewOnly) "If you punch out now" else "Hours worked",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${hours}h ${mins}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview
@Composable
private fun PunchDialogPreview() {
    FieldPharmaTheme {
        PunchDialog(attendance = null, busy = false, onPunchIn = {}, onPunchOut = {}, onDismiss = {})
    }
}
