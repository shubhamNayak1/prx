package com.baseras.fieldpharma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

/**
 * Semi-transparent scrim with a centered spinner. Drop on top of any screen
 * while an API call is in flight. Blocks taps underneath.
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    label: String? = null,
) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!label.isNullOrBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Compact inline spinner for buttons / list rows. */
@Composable
fun InlineSpinner(
    size: androidx.compose.ui.unit.Dp = 16.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = strokeWidth,
        color = color,
    )
}

/** A tiny pulsing dot used for live-sync hints. */
@Composable
fun LiveDot(color: Color = MaterialTheme.colorScheme.secondary) {
    Box(
        Modifier.size(8.dp).clip(CircleShape).background(color),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun LoadingOverlayPreview() {
    FieldPharmaTheme {
        Box(Modifier.fillMaxSize()) {
            LoadingOverlay(visible = true, label = "Checking you out…")
        }
    }
}
