package com.baseras.fieldpharma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.*

enum class PillTone { Success, Warn, Error, Info, Neutral, Primary, Accent }

/**
 * A short, high-contrast label — attendance state, call status, approval
 * state. Use at most 3 words.
 */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone = PillTone.Neutral,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = toneColors(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Just a dot — for subtle "next" or "live" indicators. */
@Composable
fun StatusDot(tone: PillTone = PillTone.Accent, modifier: Modifier = Modifier) {
    val (_, fg) = toneColors(tone)
    Box(modifier.size(8.dp).clip(CircleShape).background(fg))
}

@Composable
private fun toneColors(tone: PillTone): Pair<Color, Color> = when (tone) {
    PillTone.Success -> Success50 to Success700
    PillTone.Warn    -> Warn50 to Warn700
    PillTone.Error   -> Error50 to Error700
    PillTone.Info    -> Info50 to Info700
    PillTone.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    PillTone.Accent  -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    PillTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun StatusPillPreview() {
    FieldPharmaTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill("Punched in", PillTone.Success)
            StatusPill("Pending", PillTone.Warn)
            StatusPill("Rejected", PillTone.Error)
            StatusPill("Draft", PillTone.Neutral)
            StatusPill("Next stop", PillTone.Accent)
            StatusPill("Live", PillTone.Info)
        }
    }
}
