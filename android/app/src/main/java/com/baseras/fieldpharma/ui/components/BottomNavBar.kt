package com.baseras.fieldpharma.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

enum class BottomTab { Home, Plan, Visits, More }

data class BottomNavSpec(
    val tab: BottomTab,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
)

val DefaultTabs = listOf(
    BottomNavSpec(BottomTab.Home,   "Home",   Icons.Filled.Home,       Icons.Outlined.Home),
    BottomNavSpec(BottomTab.Plan,   "Plan",   Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomNavSpec(BottomTab.Visits, "Visits", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    BottomNavSpec(BottomTab.More,   "More",   Icons.Filled.Menu,       Icons.Outlined.Menu),
)

@Composable
fun BottomNavBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    tabs: List<BottomNavSpec> = DefaultTabs,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        tabs.forEach { spec ->
            val isSelected = spec.tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(spec.tab) },
                icon = {
                    Icon(if (isSelected) spec.iconSelected else spec.iconUnselected, null)
                },
                label = { Text(spec.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun BottomNavPreview() {
    FieldPharmaTheme {
        BottomNavBar(selected = BottomTab.Home, onSelect = {})
    }
}

