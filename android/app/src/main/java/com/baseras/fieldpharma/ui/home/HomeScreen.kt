package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.billing.BillingSnapshot
import com.baseras.fieldpharma.billing.BillingStatus
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import com.baseras.fieldpharma.ui.theme.LocalSpacing

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
    val billing by app.billingStore.state.collectAsState()
    var selectedTab by remember { mutableStateOf(BottomTab.Home) }

    // TODO: wire real data (attendance.todayStatus + tourPlan.today + visits.todayCounts).
    // For first paint while repositories are being wired up, we show a sensible
    // placeholder so the shape of the screen is correct from day one.
    val homeState = remember(user) {
        HomeState(
            firstName = user?.name?.split(" ")?.firstOrNull() ?: "there",
            greeting = pickGreeting(),
            attendance = AttendanceState.NotPunched,
            callsDone = 0,
            callsPlanned = 0,
            nextStop = null,
            upcomingStops = emptyList(),
            pendingSyncCount = 0,
            pendingSamples = 0,
            pendingExpenses = 0,
        )
    }

    HomeScaffold(
        state = homeState.copy(pendingSyncCount = pendingCount),
        billing = billing,
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        onAttendance = onAttendance,
        onClients = onClients,
        onTourPlans = onTourPlans,
        onVisits = onVisits,
        onExpenses = onExpenses,
        onSamples = onSamples,
        onEdetail = onEdetail,
        onRcpa = onRcpa,
        onLogout = onLogout,
    )
}

/* ─────────────── State type ─────────────── */

data class HomeState(
    val firstName: String,
    val greeting: String,
    val attendance: AttendanceState,
    val callsDone: Int,
    val callsPlanned: Int,
    val nextStop: NextStop?,
    val upcomingStops: List<PlanStop>,
    val pendingSyncCount: Int,
    val pendingSamples: Int,
    val pendingExpenses: Int,
)

/* ─────────────── The pure, preview-friendly screen ─────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    state: HomeState,
    billing: BillingSnapshot,
    selectedTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
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
    val sp = LocalSpacing.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopHeader(firstName = state.firstName, onLogout = onLogout)
        },
        bottomBar = {
            BottomNavBar(
                selected = selectedTab,
                onSelect = { tab ->
                    onSelectTab(tab)
                    when (tab) {
                        BottomTab.Plan -> onTourPlans()
                        BottomTab.Visits -> onVisits()
                        BottomTab.More, BottomTab.Home -> Unit
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = sp.lg, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
        ) {
            // ─── trial countdown (only during TRIAL) ───
            billing.trialDaysLeft?.let { days ->
                item {
                    TrialBanner(days = days)
                }
            }

            // ─── hero ───
            item {
                HeroStatusCard(
                    greeting = state.greeting,
                    attendance = state.attendance,
                    callsDone = state.callsDone,
                    callsPlanned = state.callsPlanned,
                    nextStop = state.nextStop,
                    onPrimaryAction = {
                        when (state.attendance) {
                            is AttendanceState.PunchedIn -> onVisits()
                            else -> onAttendance()
                        }
                    },
                )
            }

            // ─── quick actions ───
            item {
                QuickActionsRow(
                    actions = listOf(
                        QuickAction("Add client", Icons.Default.PersonAdd, onClients),
                        QuickAction("Samples", Icons.Default.CardGiftcard, onSamples),
                        QuickAction("Expense", Icons.Default.Receipt, onExpenses),
                        QuickAction("RCPA", Icons.Default.BarChart, onRcpa),
                        QuickAction("E-detail", Icons.Default.Slideshow, onEdetail),
                    ),
                )
            }

            // ─── today's plan ───
            item {
                SectionHeader(
                    title = "Today's plan",
                    actionLabel = if (state.upcomingStops.isNotEmpty()) "See all" else null,
                    onAction = { onTourPlans() },
                )
            }

            if (state.upcomingStops.isEmpty()) {
                item { EmptyPlanCard(onPlan = onTourPlans) }
            } else {
                items(state.upcomingStops, key = { it.time + it.name }) { stop ->
                    PlanListItem(stop = stop, onClick = onVisits)
                }
            }

            // ─── pending ───
            if (state.pendingSyncCount + state.pendingSamples + state.pendingExpenses > 0) {
                item { SectionHeader("Pending") }
                item {
                    PendingList(
                        syncCount = state.pendingSyncCount,
                        samples = state.pendingSamples,
                        expenses = state.pendingExpenses,
                    )
                }
            }

            item { Spacer(Modifier.height(sp.lg)) }
        }
    }
}

/* ─────────────── Smaller composables ─────────────── */

@Composable
private fun TopHeader(firstName: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = firstName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "FieldPharma",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                firstName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = { /* TODO notifications */ }) {
            Icon(
                Icons.Outlined.NotificationsNone,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onLogout) {
            Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign out",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrialBanner(days: Int) {
    val urgent = days <= 2
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (urgent) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Timelapse,
                null,
                tint = if (urgent) MaterialTheme.colorScheme.onErrorContainer
                       else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (days == 0) "Trial ends today" else "Trial · $days day${if (days == 1) "" else "s"} left",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (urgent) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Ask your admin to subscribe from the web dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (urgent) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyPlanCard(onPlan: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.EventAvailable,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No stops planned for today",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Add clients to your tour plan to see them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onPlan, shape = MaterialTheme.shapes.small) {
                Text("Open Plan")
            }
        }
    }
}

@Composable
private fun PendingList(syncCount: Int, samples: Int, expenses: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (syncCount > 0) PendingRow(
                Icons.Default.CloudUpload, "$syncCount item${if (syncCount == 1) "" else "s"} to sync",
                "Will send automatically when online"
            )
            if (samples > 0) PendingRow(
                Icons.Default.CardGiftcard, "$samples sample distribution${if (samples == 1) "" else "s"} pending",
                "Linked to today's visits"
            )
            if (expenses > 0) PendingRow(
                Icons.Default.Receipt, "$expenses expense draft${if (expenses == 1) "" else "s"}",
                "Submit for manager approval"
            )
        }
    }
}

@Composable
private fun PendingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ─────────────── Previews ─────────────── */

@Preview(name = "Home — mid-day, 4/12 done", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 900)
@Composable
private fun HomePreviewMidDay() {
    FieldPharmaTheme {
        HomeScaffold(
            state = HomeState(
                firstName = "Amit",
                greeting = "Good morning",
                attendance = AttendanceState.PunchedIn("09:12"),
                callsDone = 4,
                callsPlanned = 12,
                nextStop = NextStop("Dr. R. Sharma", distanceKm = 2.3, etaLabel = "8 min"),
                upcomingStops = listOf(
                    PlanStop("09:30", "Dr. R. Mehta",    "Cardiology · Dadar",      PlanStopState.Done),
                    PlanStop("10:30", "Apollo Pharmacy", "Chemist · Lower Parel",   PlanStopState.Done, isChemist = true),
                    PlanStop("11:30", "Dr. R. Sharma",   "Diabetology · Mahim · 2.3 km", PlanStopState.Next),
                    PlanStop("13:00", "Dr. S. Patel",    "Pediatrics · Bandra · 4.1 km", PlanStopState.Upcoming),
                    PlanStop("14:30", "Metro Chemist",   "Chemist · Khar · 5.0 km", PlanStopState.Upcoming, isChemist = true),
                ),
                pendingSyncCount = 2,
                pendingSamples = 3,
                pendingExpenses = 1,
            ),
            billing = BillingSnapshot(BillingStatus.TRIAL, cycle = null, trialEndsAt = null, fetchedAt = 0L),
            selectedTab = BottomTab.Home,
            onSelectTab = {},
            onAttendance = {}, onClients = {}, onTourPlans = {}, onVisits = {},
            onExpenses = {}, onSamples = {}, onEdetail = {}, onRcpa = {},
            onLogout = {},
        )
    }
}

@Preview(name = "Home — not punched in", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 900)
@Composable
private fun HomePreviewFresh() {
    FieldPharmaTheme {
        HomeScaffold(
            state = HomeState(
                firstName = "Amit",
                greeting = "Good morning",
                attendance = AttendanceState.NotPunched,
                callsDone = 0,
                callsPlanned = 0,
                nextStop = null,
                upcomingStops = emptyList(),
                pendingSyncCount = 0,
                pendingSamples = 0,
                pendingExpenses = 0,
            ),
            billing = BillingSnapshot(BillingStatus.ACTIVE, cycle = null, trialEndsAt = null, fetchedAt = 0L),
            selectedTab = BottomTab.Home,
            onSelectTab = {},
            onAttendance = {}, onClients = {}, onTourPlans = {}, onVisits = {},
            onExpenses = {}, onSamples = {}, onEdetail = {}, onRcpa = {},
            onLogout = {},
        )
    }
}

@Preview(name = "Home — day complete", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 900)
@Composable
private fun HomePreviewComplete() {
    FieldPharmaTheme {
        HomeScaffold(
            state = HomeState(
                firstName = "Amit",
                greeting = "Good evening",
                attendance = AttendanceState.PunchedIn("09:12"),
                callsDone = 12,
                callsPlanned = 12,
                nextStop = null,
                upcomingStops = listOf(
                    PlanStop("09:30", "Dr. R. Mehta", "Cardiology · Dadar", PlanStopState.Done),
                    PlanStop("13:00", "Dr. S. Patel", "Pediatrics · Bandra", PlanStopState.Done),
                ),
                pendingSyncCount = 0,
                pendingSamples = 0,
                pendingExpenses = 0,
            ),
            billing = BillingSnapshot(BillingStatus.ACTIVE, cycle = null, trialEndsAt = null, fetchedAt = 0L),
            selectedTab = BottomTab.Home,
            onSelectTab = {},
            onAttendance = {}, onClients = {}, onTourPlans = {}, onVisits = {},
            onExpenses = {}, onSamples = {}, onEdetail = {}, onRcpa = {},
            onLogout = {},
        )
    }
}
