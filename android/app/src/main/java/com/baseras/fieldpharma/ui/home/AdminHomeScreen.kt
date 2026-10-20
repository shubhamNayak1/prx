package com.baseras.fieldpharma.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
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
import com.baseras.fieldpharma.billing.BillingSnapshot
import com.baseras.fieldpharma.billing.BillingStatus
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import com.baseras.fieldpharma.ui.theme.LocalSpacing

/* ─────────────── State ─────────────── */

data class AdminHomeState(
    val firstName: String,
    val companyName: String,
    val totalUsers: Int,
    val activeToday: Int,
    val monthCalls: Int,
    val monthSpendInr: Int,
    val pendingSignups: Int,
    val billing: BillingSnapshot,
)

/* ─────────────── Public composable ─────────────── */

@Composable
fun AdminHomeScreen(
    onPeople: () -> Unit,
    onInvite: () -> Unit,
    onBilling: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current
    val billing by app.billingStore.state.collectAsState()
    var kpis by remember { mutableStateOf<com.baseras.fieldpharma.data.remote.RoleKpis?>(null) }

    LaunchedEffect(user?.id) {
        app.dashboardRepo.roleSummary().onSuccess { kpis = it.kpis }
    }

    val state = AdminHomeState(
        firstName = user?.name?.split(" ")?.firstOrNull() ?: "there",
        companyName = "Your company",
        totalUsers = kpis?.totalUsers ?: 0,
        activeToday = kpis?.activeToday ?: 0,
        monthCalls = kpis?.monthVisits ?: 0,
        monthSpendInr = kpis?.monthSpendInr?.toInt() ?: 0,
        pendingSignups = kpis?.pendingClients ?: 0,
        billing = billing,
    )

    AdminHomeScaffold(
        state = state,
        onPeople = onPeople,
        onInvite = onInvite,
        onBilling = onBilling,
        onMore = onMore,
        onLogout = onLogout,
    )
}

/* ─────────────── Scaffold ─────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHomeScaffold(
    state: AdminHomeState,
    onPeople: () -> Unit,
    onInvite: () -> Unit,
    onBilling: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val sp = LocalSpacing.current
    var tab by remember { mutableStateOf(BottomTab.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ManagerTopHeader(state.firstName, roleBadge = "Admin · ${state.companyName}", onLogout = onLogout) },
        bottomBar = {
            BottomNavBar(
                selected = tab,
                onSelect = { picked ->
                    tab = picked
                    when (picked) {
                        BottomTab.Plan -> onPeople()
                        BottomTab.Visits -> onBilling()
                        BottomTab.More -> onMore()
                        BottomTab.Home -> Unit
                    }
                },
                tabs = adminTabs(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = sp.lg, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
        ) {
            // ─── trial / billing banner ───
            item { BillingBanner(state.billing, onBilling = onBilling) }

            // ─── hero ───
            item {
                CompanyHero(
                    greeting = pickGreeting(),
                    companyName = state.companyName,
                    totalUsers = state.totalUsers,
                    activeToday = state.activeToday,
                )
            }

            // ─── KPIs ───
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(
                        label = "People",
                        value = state.totalUsers.toString(),
                        sub = "${state.activeToday} active today",
                        icon = Icons.Default.Groups,
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = "Calls MTD",
                        value = formatCompact(state.monthCalls),
                        sub = "across all teams",
                        icon = Icons.Default.LocationOn,
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = "Spend",
                        value = "₹${formatCompact(state.monthSpendInr)}",
                        sub = "this month",
                        icon = Icons.Default.Receipt,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ─── quick actions ───
            item {
                QuickActionsRow(
                    actions = listOf(
                        QuickAction("Invite user", Icons.Default.PersonAdd, onInvite),
                        QuickAction("Billing", Icons.Default.CreditCard, onBilling),
                        QuickAction("People", Icons.Default.People, onPeople),
                        QuickAction("More", Icons.Default.Settings, onMore),
                    ),
                )
            }

            // ─── pending signups ───
            if (state.pendingSignups > 0) {
                item { SectionHeader("Awaiting your approval") }
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PersonAddAlt,
                                null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${state.pendingSignups} new user signup${if (state.pendingSignups == 1) "" else "s"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    "Review their role + manager assignment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                )
                            }
                            TextButton(onClick = onPeople) {
                                Text("Review", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            // ─── setup checklist (first-run) ───
            if (state.totalUsers <= 1) {
                item { SectionHeader("Finish setup") }
                item { OnboardingChecklist(onPeople = onPeople) }
            }

            item { Spacer(Modifier.height(sp.lg)) }
        }
    }
}

/* ─────────────── Pieces ─────────────── */

@Composable
private fun BillingBanner(billing: BillingSnapshot, onBilling: () -> Unit) {
    when (billing.status) {
        BillingStatus.TRIAL -> {
            val days = billing.trialDaysLeft ?: 0
            val urgent = days <= 2
            Surface(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onBilling() },
                shape = MaterialTheme.shapes.medium,
                color = if (urgent) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            "Pick a plan to keep your team's data flowing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (urgent) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    FilledTonalButton(onClick = onBilling, shape = MaterialTheme.shapes.small) {
                        Text("Upgrade", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        BillingStatus.PAST_DUE,
        BillingStatus.LOCKED -> {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onBilling() },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Subscription on hold — pay to restore access",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onBilling) {
                        Text("Open", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun CompanyHero(
    greeting: String,
    companyName: String,
    totalUsers: Int,
    activeToday: Int,
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
                Spacer(Modifier.height(4.dp))
                Text(
                    companyName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                val line = if (totalUsers == 0) {
                    "Start by inviting your first Field Person or Manager."
                } else {
                    "$totalUsers team members · $activeToday active today"
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
private fun OnboardingChecklist(onPeople: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            ChecklistRow(Icons.Default.PersonAdd, "Invite your first Senior Manager", "Starts the hierarchy", onPeople)
            ChecklistRow(Icons.Default.LocalHospital, "Add client master data", "Doctors, chemists, stockists") { /* TODO */ }
            ChecklistRow(Icons.Default.Receipt, "Set TA / DA rates per grade", "So expenses auto-calculate") { /* TODO */ }
            ChecklistRow(Icons.Default.CreditCard, "Pick a billing cycle", "Monthly, quarterly or annual") { /* TODO */ }
        }
    }
}

@Composable
private fun ChecklistRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun adminTabs() = listOf(
    BottomNavSpec(BottomTab.Home,   "Home",    Icons.Filled.Home,       Icons.Outlined.Home),
    BottomNavSpec(BottomTab.Plan,   "People",  Icons.Filled.People,     Icons.Outlined.People),
    BottomNavSpec(BottomTab.Visits, "Billing", Icons.Filled.CreditCard, Icons.Outlined.CreditCard),
    BottomNavSpec(BottomTab.More,   "More",    Icons.Filled.Menu,       Icons.Outlined.Menu),
)

private fun formatCompact(n: Int): String = when {
    n >= 10_000_000 -> "${(n / 1_000_000.0).let { "%.1f".format(it) }}M"
    n >= 1_00_000   -> "${(n / 1_000.0).let { "%.1f".format(it) }}K"
    n >= 1_000      -> "${(n / 1_000.0).let { "%.1f".format(it) }}K"
    else -> n.toString()
}

/* ─────────────── Previews ─────────────── */

@Preview(name = "Admin — trial, early days", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1100)
@Composable
private fun AdminHomePreviewTrial() {
    FieldPharmaTheme {
        AdminHomeScaffold(
            state = AdminHomeState(
                firstName = "Shubham",
                companyName = "Acme Pharma",
                totalUsers = 1,
                activeToday = 0,
                monthCalls = 0,
                monthSpendInr = 0,
                pendingSignups = 0,
                billing = BillingSnapshot(BillingStatus.TRIAL, cycle = null, trialEndsAt = null, fetchedAt = 0L),
            ),
            onPeople = {}, onInvite = {}, onBilling = {}, onMore = {}, onLogout = {},
        )
    }
}

@Preview(name = "Admin — running company", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1100)
@Composable
private fun AdminHomePreviewActive() {
    FieldPharmaTheme {
        AdminHomeScaffold(
            state = AdminHomeState(
                firstName = "Shubham",
                companyName = "Acme Pharma",
                totalUsers = 127,
                activeToday = 108,
                monthCalls = 5240,
                monthSpendInr = 2_40_000,
                pendingSignups = 3,
                billing = BillingSnapshot(BillingStatus.ACTIVE, cycle = "ANNUAL", trialEndsAt = null, fetchedAt = 0L),
            ),
            onPeople = {}, onInvite = {}, onBilling = {}, onMore = {}, onLogout = {},
        )
    }
}

@Preview(name = "Admin — locked", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun AdminHomePreviewLocked() {
    FieldPharmaTheme {
        AdminHomeScaffold(
            state = AdminHomeState(
                firstName = "Shubham",
                companyName = "Acme Pharma",
                totalUsers = 24,
                activeToday = 0,
                monthCalls = 0,
                monthSpendInr = 0,
                pendingSignups = 0,
                billing = BillingSnapshot(BillingStatus.LOCKED, cycle = null, trialEndsAt = null, fetchedAt = 0L),
            ),
            onPeople = {}, onInvite = {}, onBilling = {}, onMore = {}, onLogout = {},
        )
    }
}
