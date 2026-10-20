package com.baseras.fieldpharma.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

enum class Trend { Up, Down, Flat }

/**
 * Dense single stat with optional sub-metric and trend chip. Pair of 2-3 of
 * these across the top of a manager/admin home gives an at-a-glance read.
 */
@Composable
fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = null,
    trend: Trend? = null,
    accentColor: Color? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (icon != null) Icon(
                    icon, null,
                    tint = accentColor ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (trend != null) {
                    Spacer(Modifier.width(6.dp))
                    TrendPill(trend)
                }
            }
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendPill(trend: Trend) {
    val (label, tone) = when (trend) {
        Trend.Up   -> "▲" to PillTone.Success
        Trend.Down -> "▼" to PillTone.Error
        Trend.Flat -> "·"  to PillTone.Neutral
    }
    StatusPill(label, tone)
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun KpiRowPreview() {
    FieldPharmaTheme {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiTile("Active today", "42 / 50", sub = "84%", trend = Trend.Up, modifier = Modifier.weight(1f))
            KpiTile("Attainment", "72%", sub = "vs 68% last mo", trend = Trend.Up, modifier = Modifier.weight(1f))
            KpiTile("Pending", "9", sub = "approvals", trend = Trend.Flat, modifier = Modifier.weight(1f))
        }
    }
}
