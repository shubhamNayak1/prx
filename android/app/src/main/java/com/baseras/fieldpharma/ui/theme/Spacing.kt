package com.baseras.fieldpharma.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 4dp grid. Use via LocalSpacing.current.lg instead of literal dp values so
 * screens stay consistent and dark-mode / size tweaks live in one place.
 */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs:  Dp = 4.dp,
    val sm:  Dp = 8.dp,
    val md:  Dp = 12.dp,
    val lg:  Dp = 16.dp,
    val xl:  Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
