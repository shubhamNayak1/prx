package com.baseras.fieldpharma.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

enum class SettingsVariant { FieldPerson, Manager, Admin }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(variant: SettingsVariant, onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current

    // Local-only toggles for v1 — not persisted beyond this process.
    var notificationsOn by remember { mutableStateOf(true) }
    var hapticsOn by remember { mutableStateOf(true) }
    var dataSaver by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("App") }
            item {
                ToggleRow(Icons.Default.Notifications, "Notifications", "Push alerts and reminders",
                    checked = notificationsOn, onCheckedChange = { notificationsOn = it })
            }
            item {
                ToggleRow(Icons.Default.Vibration, "Haptics", "Tap feedback for key actions",
                    checked = hapticsOn, onCheckedChange = { hapticsOn = it })
            }
            item {
                ToggleRow(Icons.Default.DataUsage, "Data saver", "Only sync on Wi-Fi",
                    checked = dataSaver, onCheckedChange = { dataSaver = it })
            }

            if (variant == SettingsVariant.Admin) {
                item { SectionHeader("Company") }
                item { StaticRow(Icons.Default.Business, "Company profile", "Name, GSTIN, billing contact") }
                item { StaticRow(Icons.Default.Receipt, "TA / DA rates", "Per-grade travel and daily allowance") }
                item { StaticRow(Icons.Default.AccountTree, "Hierarchy", "Reporting lines across the org") }
                item { StaticRow(Icons.Default.CreditCard, "Billing", "Plan, invoices, GSTIN") }
            }

            item { SectionHeader("About") }
            item {
                StaticRow(Icons.Default.Info, "Version", "0.1.0 · build 1")
            }
            if (user != null) {
                item {
                    StaticRow(Icons.Default.Person, "Signed in", "${user.name} · ${user.email}")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun StaticRow(icon: ImageVector, title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun SettingsPreview() {
    FieldPharmaTheme {
        SettingsScreen(variant = SettingsVariant.Admin, onBack = {})
    }
}
