package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp

private data class TileSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAttendance: () -> Unit,
    onClients: () -> Unit,
    onTourPlans: () -> Unit,
    onVisits: () -> Unit,
    onExpenses: () -> Unit,
    onSamples: () -> Unit,
    onEdetail: () -> Unit,
    onRcpa: () -> Unit,
    onLogout: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current
    val pendingCount by app.attendanceRepo.observePendingCount().collectAsState(initial = 0)

    val tiles = listOf(
        TileSpec(Icons.Default.AccessTime, "Attendance", "Punch in / out + selfie", onAttendance),
        TileSpec(Icons.Default.LocalHospital, "Clients", "Doctors, chemists, stockists", onClients),
        TileSpec(Icons.Default.LocationOn, "Visits / DCR", "Check-in, notes, check-out", onVisits),
        TileSpec(Icons.Default.CalendarMonth, "Tour plans", "Upcoming visits schedule", onTourPlans),
        TileSpec(Icons.Default.Receipt, "Expenses", "TA / DA / actuals", onExpenses),
        TileSpec(Icons.Default.CardGiftcard, "Samples & gifts", "Stock balance + distribute", onSamples),
        TileSpec(Icons.Default.Slideshow, "E-detailing", "Offline presentation decks", onEdetail),
        TileSpec(Icons.Default.BarChart, "RCPA", "Competitor audit at chemists", onRcpa),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hi, ${user?.name?.split(" ")?.first() ?: "MR"}") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pendingCount > 0) {
                item {
                    ElevatedCard {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$pendingCount item(s) pending sync", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            items(tiles) { t -> Tile(t.icon, t.title, t.subtitle, t.onClick) }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
