package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import com.baseras.fieldpharma.ui.theme.LocalSpacing

/* ─────────────── State ─────────────── */

data class RmHomeState(
    val firstName: String,
    val activeToday: Int,
    val teamSize: Int,
    val callsSoFar: Int,
    val callsPlanned: Int,
    val attainmentPct: Int,
    val pendingTourPlans: Int,
    val pendingExpenses: Int,
    val pendingClients: Int,
    val team: List<TeamMember>,
)

/* ─────────────── Public composable ─────────────── */

@Composable
fun RegionalManagerHomeScreen(
    onApprovals: () -> Unit,
    onTeam: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current
    var summary by remember { mutableStateOf<com.baseras.fieldpharma.data.remote.RoleSummaryRes?>(null) }

    LaunchedEffect(user?.id) {
        app.dashboardRepo.roleSummary().onSuccess { summary = it }
    }

    val state = RmHomeState(
        firstName = user?.name?.split(" ")?.firstOrNull() ?: "there",
        activeToday = summary?.kpis?.activeToday ?: 0,
        teamSize = summary?.kpis?.totalReps ?: 0,
        callsSoFar = summary?.kpis?.callsToday ?: 0,
        callsPlanned = summary?.kpis?.callsPlannedToday ?: 0,
        attainmentPct = summary?.kpis?.attainmentPct ?: 0,
        pendingTourPlans = summary?.kpis?.pendingTourPlans ?: 0,
        pendingExpenses = summary?.kpis?.pendingExpensesCount ?: 0,
        pendingClients = summary?.kpis?.pendingClients ?: 0,
        team = summary?.team.orEmpty().map { r ->
            TeamMember(
                name = r.name,
                roleLabel = "Field · ${r.grade ?: "—"}",
                status = when (r.status) {
                    "ActiveToday" -> TeamMemberStatus.ActiveToday
                    "NotPunched" -> TeamMemberStatus.NotPunched
                    "Flagged" -> TeamMemberStatus.Flagged
                    else -> TeamMemberStatus.OffField
                },
                progressLine = "${r.callsToday} / ${r.callsPlanned} calls",
            )
        },
    )
    RmHomeScaffold(
        state = state,
        onApprovals = onApprovals,
        onTeam = onTeam,
        onMore = onMore,
        onLogout = onLogout,
    )
}

/* ─────────────── Preview-friendly scaffold ─────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RmHomeScaffold(
    state: RmHomeState,
    onApprovals: () -> Unit,
    onTeam: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val sp = LocalSpacing.current
    var tab by remember { mutableStateOf(BottomTab.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ManagerTopHeader(state.firstName, roleBadge = "Regional Manager", onLogout = onLogout) },
        bottomBar = {
            BottomNavBar(
                selected = tab,
                onSelect = { picked ->
                    tab = picked
                    when (picked) {
                        BottomTab.Plan -> onApprovals()
                        BottomTab.Visits -> onTeam()
                        BottomTab.More -> onMore()
                        BottomTab.Home -> Unit
                    }
                },
                tabs = managerTabs(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = sp.lg, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
        ) {
            // ─── hero: region snapshot ───
            item {
                RegionHeroCard(
                    greeting = pickGreeting(),
                    activeToday = state.activeToday,
                    teamSize = state.teamSize,
                    callsSoFar = state.callsSoFar,
                    callsPlanned = state.callsPlanned,
                    attainmentPct = state.attainmentPct,
                )
            }

            // ─── KPIs ───
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(
                        label = "Active today",
                        value = "${state.activeToday}/${state.teamSize}",
                        sub = if (state.teamSize > 0) "${(state.activeToday * 100 / state.teamSize)}%" else null,
                        icon = Icons.Default.People,
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = "Calls",
                        value = "${state.callsSoFar}",
                        sub = "of ${state.callsPlanned} planned",
                        icon = Icons.Default.LocationOn,
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = "Attainment",
                        value = "${state.attainmentPct}%",
                        sub = "this month",
                        icon = Icons.Default.TrendingUp,
                        trend = if (state.attainmentPct >= 70) Trend.Up else Trend.Flat,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ─── approvals ───
            val pendingTotal = state.pendingTourPlans + state.pendingExpenses + state.pendingClients
            if (pendingTotal > 0) {
                item { SectionHeader("Awaiting your approval", actionLabel = "Open", onAction = onApprovals) }
                item {
                    ApprovalsCard(
                        tourPlans = state.pendingTourPlans,
                        expenses = state.pendingExpenses,
                        clients = state.pendingClients,
                        onOpen = onApprovals,
                    )
                }
            }

            // ─── team ───
            item { SectionHeader("My field team", actionLabel = "See all", onAction = onTeam) }
            if (state.team.isEmpty()) {
                item { EmptyTeamCard() }
            } else {
                items(state.team, key = { it.name }) { m ->
                    TeamMemberItem(member = m, onClick = onTeam)
                }
            }

            item { Spacer(Modifier.height(sp.lg)) }
        }
    }
}

/* ─────────────── Small composables ─────────────── */

@Composable
internal fun ManagerTopHeader(firstName: String, roleBadge: String, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                firstName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                roleBadge,
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
            Icon(Icons.Outlined.NotificationsNone, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RegionHeroCard(
    greeting: String,
    activeToday: Int,
    teamSize: Int,
    callsSoFar: Int,
    callsPlanned: Int,
    attainmentPct: Int,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Box(
                Modifier.fillMaxWidth().height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to primary.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        )
                    )
            )
            Column(Modifier.padding(20.dp)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Region at a glance",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                val summary = if (teamSize == 0) {
                    "No field team yet. Add your first Field Person from More."
                } else {
                    "$activeToday of $teamSize reps active · $callsSoFar / $callsPlanned calls so far · $attainmentPct% attainment this month"
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ApprovalsCard(
    tourPlans: Int,
    expenses: Int,
    clients: Int,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            if (tourPlans > 0) ApprovalRow(Icons.Default.CalendarMonth, "$tourPlans tour plan${if (tourPlans == 1) "" else "s"}", "Awaiting your review")
            if (expenses > 0) ApprovalRow(Icons.Default.Receipt, "$expenses expense claim${if (expenses == 1) "" else "s"}", "Pending approval")
            if (clients > 0) ApprovalRow(Icons.Default.PersonAdd, "$clients new client${if (clients == 1) "" else "s"}", "Added by your team")
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onOpen, shape = MaterialTheme.shapes.small) {
                Text("Open approval queue", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ApprovalRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun EmptyTeamCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PeopleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("No field team yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ask your Admin to invite Field Persons under you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun managerTabs() = listOf(
    BottomNavSpec(BottomTab.Home,   "Home",      Icons.Filled.Home,       Icons.Outlined.Home),
    BottomNavSpec(BottomTab.Plan,   "Approvals", Icons.Filled.FactCheck,  Icons.Outlined.FactCheck),
    BottomNavSpec(BottomTab.Visits, "Team",      Icons.Filled.People,     Icons.Outlined.People),
    BottomNavSpec(BottomTab.More,   "More",      Icons.Filled.Menu,       Icons.Outlined.Menu),
)

/* ─────────────── Previews ─────────────── */

@Preview(name = "Regional Manager — busy day", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun RmHomePreview() {
    FieldPharmaTheme {
        RmHomeScaffold(
            state = RmHomeState(
                firstName = "Suresh",
                activeToday = 8,
                teamSize = 10,
                callsSoFar = 42,
                callsPlanned = 110,
                attainmentPct = 72,
                pendingTourPlans = 3,
                pendingExpenses = 5,
                pendingClients = 1,
                team = listOf(
                    TeamMember("Amit Singh", "Field · Delhi", TeamMemberStatus.ActiveToday, "4 / 10 calls"),
                    TeamMember("Priya Patel", "Field · Delhi", TeamMemberStatus.ActiveToday, "7 / 10 calls"),
                    TeamMember("Ravi Verma", "Field · NCR", TeamMemberStatus.NotPunched, "0 / 8 calls"),
                    TeamMember("Neha Gupta", "Field · Delhi", TeamMemberStatus.Flagged, "Out of radius ×2"),
                ),
            ),
            onApprovals = {}, onTeam = {}, onMore = {}, onLogout = {},
        )
    }
}

@Preview(name = "Regional Manager — fresh", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun RmHomeEmptyPreview() {
    FieldPharmaTheme {
        RmHomeScaffold(
            state = RmHomeState(
                firstName = "Suresh",
                activeToday = 0,
                teamSize = 0,
                callsSoFar = 0,
                callsPlanned = 0,
                attainmentPct = 0,
                pendingTourPlans = 0,
                pendingExpenses = 0,
                pendingClients = 0,
                team = emptyList(),
            ),
            onApprovals = {}, onTeam = {}, onMore = {}, onLogout = {},
        )
    }
}
