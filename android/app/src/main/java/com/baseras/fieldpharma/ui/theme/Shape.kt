package com.baseras.fieldpharma.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // tiny pills, chips
    small      = RoundedCornerShape(12.dp),  // buttons, inputs
    medium     = RoundedCornerShape(16.dp),  // cards, list items
    large      = RoundedCornerShape(20.dp),  // hero, modals
    extraLarge = RoundedCornerShape(28.dp),  // bottom sheets
)
