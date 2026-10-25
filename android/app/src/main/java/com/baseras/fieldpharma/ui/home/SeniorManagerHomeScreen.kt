package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
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

data class RegionSummary(
    val regionalManagerName: String,
    val regionLabel: String,          // "West · 4 reps"
    val activeReps: Int,
    val totalReps: Int,
    val attainmentPct: Int,
    val pendingApprovals: Int,
)

data class SmHomeState(
    val firstName: String,
    val totalReps: Int,
    val activeReps: Int,
    val callsMtd: Int,
    val callsPlannedMtd: Int,
    val attainmentPct: Int,
    val pendingEscalations: Int,
    val regions: List<RegionSummary>,
)

/* ─────────────── Public composable ─────────────── */

@Composable
fun SeniorManagerHomeScreen(
    onApprovals: () -> Unit,
    onRegions: () -> Unit,
    onReports: () -> Unit,
    onJointWork: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current
    var summary by remember { mutableStateOf<com.baseras.fieldpharma.data.remote.RoleSummaryRes?>(null) }

    LaunchedEffect(user?.id) {
        app.dashboardRepo.roleSummary().onSuccess { summary = it }
    }

    val state = SmHomeState(
        firstName = user?.name?.split(" ")?.firstOrNull() ?: "there",
        totalReps = summary?.kpis?.totalReps ?: 0,
        activeReps = summary?.kpis?.activeToday ?: 0,
        callsMtd = summary?.kpis?.monthVisits ?: 0,
        callsPlannedMtd = summary?.kpis?.monthPlanned ?: 0,
        attainmentPct = summary?.kpis?.attainmentPct ?: 0,
        pendingEscalations = (summary?.kpis?.pendingExpensesCount ?: 0),
        regions = summary?.team.orEmpty().map { r ->
            RegionSummary(
                regionalManagerName = r.name,
                regionLabel = "${r.grade ?: "—"} · ${r.totalReps} rep${if (r.totalReps == 1) "" else "s"}",
                activeReps = r.activeToday,
                totalReps = r.totalReps,
                attainmentPct = r.attainmentPct,
                pendingApprovals = 0, // TODO: enrich backend with per-RM pending counts
            )
        },
    )
    SmHomeScaffold(
        state = state,
        onApprovals = onApprovals,
        onRegions = onRegions,
        onReports = onReports,
        onJointWork = onJointWork,
        onMore = onMore,
        onLogout = onLogout,
    )
}

/* ─────────────── Scaffold ─────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmHomeScaffold(
    state: SmHomeState,
    onApprovals: () -> Unit,
    onRegions: () -> Unit,
    onReports: () -> Unit,
    onJointWork: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val sp = LocalSpacing.current
    var tab by remember { mutableStateOf(BottomTab.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ManagerTopHeader(state.firstName, roleBadge = "Senior Manager", onLogout = onLogout) },
        bottomBar = {
            BottomNavBar(
                selected = tab,
                onSelect = { picked ->
                    tab = picked
                    when (picked) {
                        BottomTab.Plan -> onApprovals()
                        BottomTab.Visits -> onRegions()
                        BottomTab.More -> onMore()
                        BottomTab.Home -> Unit
                    }
                },
                tabs = seniorManagerTabs(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = sp.lg, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
        ) {
            // ─── hero: multi-region rollup ───
            item {
                MultiRegionHero(
                    greeting = pickGreeting(),
                    regionsCount = state.regions.size,
                    totalReps = state.totalReps,
                    activeReps = state.activeReps,
                    attainmentPct = state.attainmentPct,
                )
            }

            // ─── KPIs ───
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(
                        label = "Field strength",
                        value = "${state.activeReps}/${state.totalReps}",
                        sub = if (state.totalReps > 0) "${(state.activeReps * 100 / state.totalReps)}% active" else null,
                        icon = Icons.Default.Groups,
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = "Calls MTD",
                        value = state.callsMtd.toString(),
                        sub = "of ${state.callsPlannedMtd} planned",
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

            // ─── quick actions ───
            item {
                QuickActionsRow(
                    actions = listOf(
                        QuickAction("Approvals", Icons.Default.FactCheck, onApprovals),
                        QuickAction("Joint work", Icons.Default.PersonSearch, onJointWork),
                        QuickAction("Reports", Icons.Default.Analytics, onReports),
                        QuickAction("Regions", Icons.Default.Map, onRegions),
                    ),
                )
            }

            // ─── escalations ───
            if (state.pendingEscalations > 0) {
                item {
                    EscalationBanner(count = state.pendingEscalations, onOpen = onApprovals)
                }
            }

            // ─── regions ───
            item { SectionHeader("Regions", actionLabel = "See all", onAction = onRegions) }
            if (state.regions.isEmpty()) {
                item { EmptyRegionsCard() }
            } else {
                items(state.regions, key = { it.regionalManagerName }) { r ->
                    RegionRow(r, onClick = onRegions)
                }
            }

            item { Spacer(Modifier.height(sp.lg)) }
        }
    }
}

/* ─────────────── Pieces ─────────────── */

@Composable
private fun MultiRegionHero(
    greeting: String,
    regionsCount: Int,
    totalReps: Int,
    activeReps: Int,
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
                Modifier.fillMaxWidth().height(104.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to primary.copy(alpha = 0.10f),
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
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$attainmentPct%",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "attainment",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                val line = if (regionsCount == 0) {
                    "No regions yet. Ask Admin to assign Regional Managers to you."
                } else {
                    "$regionsCount region${if (regionsCount == 1) "" else "s"} · $activeReps of $totalReps reps active today"
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RegionRow(region: RegionSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    region.regionalManagerName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(region.regionalManagerName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    region.regionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${region.attainmentPct}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${region.activeReps}/${region.totalReps} active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (region.pendingApprovals > 0) {
                    Spacer(Modifier.height(2.dp))
                    StatusPill("${region.pendingApprovals} pend.", PillTone.Warn)
                }
            }
        }
    }
}

@Composable
private fun EscalationBanner(count: Int, onOpen: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PriorityHigh,
                null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$count escalation${if (count == 1) "" else "s"} awaiting you",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Expense claims above policy or MTPs beyond deadline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
            TextButton(onClick = onOpen) {
                Text("Review", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun EmptyRegionsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("No regions assigned", style = MaterialTheme.typography.titleMedium)
            Text(
                "Admin will assign Regional Managers under you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun seniorManagerTabs() = listOf(
    BottomNavSpec(BottomTab.Home,   "Home",      Icons.Filled.Home,       Icons.Outlined.Home),
    BottomNavSpec(BottomTab.Plan,   "Approvals", Icons.Filled.FactCheck,  Icons.Outlined.FactCheck),
    BottomNavSpec(BottomTab.Visits, "Regions",   Icons.Filled.Map,        Icons.Outlined.Map),
    BottomNavSpec(BottomTab.More,   "More",      Icons.Filled.Menu,       Icons.Outlined.Menu),
)

/* ─────────────── Previews ─────────────── */

@Preview(name = "Senior Manager — healthy", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1100)
@Composable
private fun SmHomePreview() {
    FieldPharmaTheme {
        SmHomeScaffold(
            state = SmHomeState(
                firstName = "Rajesh",
                totalReps = 34,
                activeReps = 29,
                callsMtd = 1284,
                callsPlannedMtd = 1760,
                attainmentPct = 73,
                pendingEscalations = 2,
                regions = listOf(
                    RegionSummary("Suresh Sharma", "West · 10 reps", 8, 10, 72, pendingApprovals = 3),
                    RegionSummary("Priya Nair",    "South · 12 reps", 11, 12, 78, pendingApprovals = 1),
                    RegionSummary("Vikram Shah",   "North · 12 reps", 10, 12, 69, pendingApprovals = 0),
                ),
            ),
            onApprovals = {}, onRegions = {}, onReports = {}, onJointWork = {}, onMore = {}, onLogout = {},
        )
    }
}

@Preview(name = "Senior Manager — fresh", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun SmHomeEmptyPreview() {
    FieldPharmaTheme {
        SmHomeScaffold(
            state = SmHomeState(
                firstName = "Rajesh", totalReps = 0, activeReps = 0,
                callsMtd = 0, callsPlannedMtd = 0, attainmentPct = 0,
                pendingEscalations = 0, regions = emptyList(),
            ),
            onApprovals = {}, onRegions = {}, onReports = {}, onJointWork = {}, onMore = {}, onLogout = {},
        )
    }
}
