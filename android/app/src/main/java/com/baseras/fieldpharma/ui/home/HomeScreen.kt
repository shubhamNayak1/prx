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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.billing.BillingSnapshot
import com.baseras.fieldpharma.billing.BillingStatus
import com.baseras.fieldpharma.data.local.AttendanceEntity
import com.baseras.fieldpharma.data.remote.TourPlanDto
import com.baseras.fieldpharma.data.repo.AttendanceRepository
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import com.baseras.fieldpharma.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* ─────────────── Field Home VM ─────────────── */

class FieldHomeViewModel(
    private val app: FieldPharmaApp,
) : ViewModel() {
    val today = app.attendanceRepo.observeToday()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pendingSync = app.attendanceRepo.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _plan = MutableStateFlow<List<TourPlanDto>>(emptyList())
    val plan = _plan.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()
    private val _loadingPlan = MutableStateFlow(false)
    val loadingPlan = _loadingPlan.asStateFlow()

    init {
        refreshPlan()
    }

    fun refreshPlan() {
        _loadingPlan.value = true
        val todayDate = LocalDate.now().toString()
        viewModelScope.launch {
            runCatching { app.api.tourPlans(from = todayDate, to = todayDate) }
                .onSuccess { _plan.value = it.plans }
            _loadingPlan.value = false
        }
    }

    fun punchIn() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching {
                val geo = app.locationProvider.current()
                app.attendanceRepo.punchIn(geo?.lat, geo?.lng, null)
                _msg.value = "Punched in"
            }.onFailure { _msg.value = "Punch in failed: ${it.message}" }
            _busy.value = false
        }
    }

    fun punchOut() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching {
                val geo = app.locationProvider.current()
                app.attendanceRepo.punchOut(geo?.lat, geo?.lng)
                _msg.value = "Punched out"
            }.onFailure { _msg.value = "Punch out failed: ${it.message}" }
            _busy.value = false
        }
    }

    fun clearMessage() { _msg.value = null }
}

/* ─────────────── Public composable ─────────────── */

@Composable
fun HomeScreen(
    onClients: () -> Unit,
    onTourPlans: () -> Unit,
    onVisits: () -> Unit,
    onExpenses: () -> Unit,
    onSamples: () -> Unit,
    onEdetail: () -> Unit,
    onRcpa: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val vm: FieldHomeViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(FieldHomeViewModel::class) { FieldHomeViewModel(app) }
    })
    val today by vm.today.collectAsState()
    val pendingCount by vm.pendingSync.collectAsState()
    val plan by vm.plan.collectAsState()
    val loadingPlan by vm.loadingPlan.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.msg.collectAsState()
    val billing by app.billingStore.state.collectAsState()
    val user = app.authRepo.current

    var showPunchDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(BottomTab.Home) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        msg?.let {
            snackbarHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val attendance = attendanceState(today)
    val punchedIn = attendance is AttendanceState.PunchedIn

    val stops = buildStops(plan, punchedIn)
    val callsDone = stops.count { it.state == PlanStopState.Done }
    val callsPlanned = stops.size
    val nextStop = stops.firstOrNull { it.state == PlanStopState.Next }
        ?.let { NextStop(name = it.name, distanceKm = null, etaLabel = null) }

    val state = HomeState(
        firstName = user?.name?.split(" ")?.firstOrNull() ?: "there",
        greeting = pickGreeting(),
        attendance = attendance,
        callsDone = callsDone,
        callsPlanned = callsPlanned,
        nextStop = nextStop,
        upcomingStops = stops,
        pendingSyncCount = pendingCount,
        pendingSamples = 0,
        pendingExpenses = 0,
    )

    HomeScaffold(
        state = state,
        billing = billing,
        loadingPlan = loadingPlan,
        selectedTab = selectedTab,
        snackbarHost = snackbarHost,
        onSelectTab = { selectedTab = it },
        onPrimaryHero = {
            if (!punchedIn) showPunchDialog = true else onVisits()
        },
        onAttendanceClick = { showPunchDialog = true },
        onClients = onClients,
        onTourPlans = onTourPlans,
        onVisits = {
            if (!punchedIn) {
                vm.viewModelScope.launch { snackbarHost.showSnackbar("Punch in first to start a visit.") }
                showPunchDialog = true
            } else onVisits()
        },
        onExpenses = onExpenses,
        onSamples = onSamples,
        onEdetail = onEdetail,
        onRcpa = onRcpa,
        onMore = onMore,
        onLogout = onLogout,
    )

    if (showPunchDialog) {
        PunchDialog(
            attendance = today,
            busy = busy,
            onPunchIn = { vm.punchIn() },
            onPunchOut = { vm.punchOut() },
            onDismiss = { showPunchDialog = false },
        )
    }
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

/* ─────────────── The scaffold ─────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    state: HomeState,
    billing: BillingSnapshot,
    loadingPlan: Boolean,
    selectedTab: BottomTab,
    snackbarHost: SnackbarHostState,
    onSelectTab: (BottomTab) -> Unit,
    onPrimaryHero: () -> Unit,
    onAttendanceClick: () -> Unit,
    onClients: () -> Unit,
    onTourPlans: () -> Unit,
    onVisits: () -> Unit,
    onExpenses: () -> Unit,
    onSamples: () -> Unit,
    onEdetail: () -> Unit,
    onRcpa: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val sp = LocalSpacing.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopHeader(firstName = state.firstName, onLogout = onLogout) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            BottomNavBar(
                selected = selectedTab,
                onSelect = { tab ->
                    onSelectTab(tab)
                    when (tab) {
                        BottomTab.Plan -> onTourPlans()
                        BottomTab.Visits -> onVisits()
                        BottomTab.More -> onMore()
                        BottomTab.Home -> Unit
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
            billing.trialDaysLeft?.let { days ->
                item { TrialBanner(days = days) }
            }

            item {
                HeroStatusCard(
                    greeting = state.greeting,
                    attendance = state.attendance,
                    callsDone = state.callsDone,
                    callsPlanned = state.callsPlanned,
                    nextStop = state.nextStop,
                    onPrimaryAction = onPrimaryHero,
                )
            }

            item {
                QuickActionsRow(
                    actions = listOf(
                        QuickAction("Attendance", Icons.Default.AccessTime, onAttendanceClick),
                        QuickAction("Add client", Icons.Default.PersonAdd, onClients),
                        QuickAction("Samples", Icons.Default.CardGiftcard, onSamples),
                        QuickAction("Expense", Icons.Default.Receipt, onExpenses),
                        QuickAction("RCPA", Icons.Default.BarChart, onRcpa),
                        QuickAction("E-detail", Icons.Default.Slideshow, onEdetail),
                    ),
                )
            }

            item {
                SectionHeader(
                    title = "Today's plan",
                    actionLabel = if (state.upcomingStops.isNotEmpty()) "See all" else null,
                    onAction = onTourPlans,
                )
            }

            if (loadingPlan && state.upcomingStops.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        InlineSpinner(size = 22.dp)
                    }
                }
            } else if (state.upcomingStops.isEmpty()) {
                item { EmptyPlanCard(onPlan = onTourPlans) }
            } else {
                items(state.upcomingStops, key = { it.time + it.name }) { stop ->
                    PlanListItem(stop = stop, onClick = onVisits)
                }
            }

            if (state.pendingSyncCount > 0) {
                item { SectionHeader("Pending") }
                item { PendingSyncCard(count = state.pendingSyncCount) }
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
            .statusBarsPadding()
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
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Timelapse, null,
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
                Icons.Default.EventAvailable, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("No upcoming plan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nothing scheduled today — add stops in Tour Plan.",
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
private fun PendingSyncCard(count: Int) {
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
            Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$count item${if (count == 1) "" else "s"} to sync", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Will send automatically when online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ─────────────── Helpers ─────────────── */

/** Derives UI AttendanceState from the local Room row. */
internal fun attendanceState(a: AttendanceEntity?): AttendanceState {
    if (a?.punchInAt == null) return AttendanceState.NotPunched
    val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    val inTime = runCatching { fmt.format(Instant.parse(a.punchInAt)) }.getOrNull() ?: "—"
    val outIso = a.punchOutAt
    return if (outIso != null) {
        val outTime = runCatching { fmt.format(Instant.parse(outIso)) }.getOrNull() ?: "—"
        AttendanceState.PunchedOut(outTime)
    } else {
        AttendanceState.PunchedIn(inTime)
    }
}

/**
 * Flatten today's tour plans into the UI PlanStop model. Marks stops as Done
 * if the plan status is COMPLETED, Next for the first upcoming entry, else Upcoming.
 */
internal fun buildStops(plans: List<TourPlanDto>, punchedIn: Boolean): List<PlanStop> {
    val raw = plans.flatMap { p ->
        p.entries.map { e ->
            PlanStop(
                time = p.date.takeLast(5).ifBlank { "—" },
                name = e.client?.name ?: e.area ?: "Stop",
                subtitle = (e.client?.speciality ?: e.area ?: "") + (e.client?.city?.let { " · $it" } ?: ""),
                state = PlanStopState.Upcoming,
                isChemist = e.client?.type == "CHEMIST",
            )
        }
    }
    if (raw.isEmpty()) return emptyList()
    // First upcoming becomes "Next" if the user is punched in; otherwise all stay Upcoming
    return if (!punchedIn) raw else raw.mapIndexed { i, s ->
        if (i == 0) s.copy(state = PlanStopState.Next) else s
    }
}

/* ─────────────── Previews ─────────────── */

@Preview(name = "Home — mid-day, 4/12 done", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
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
                    PlanStop("09:30", "Dr. R. Mehta", "Cardiology · Dadar", PlanStopState.Done),
                    PlanStop("11:30", "Dr. R. Sharma", "Diabetology · Mahim", PlanStopState.Next),
                    PlanStop("13:00", "Dr. S. Patel", "Pediatrics · Bandra", PlanStopState.Upcoming),
                ),
                pendingSyncCount = 2,
                pendingSamples = 3,
                pendingExpenses = 1,
            ),
            billing = BillingSnapshot(BillingStatus.TRIAL, null, null, 0L),
            loadingPlan = false,
            selectedTab = BottomTab.Home,
            snackbarHost = SnackbarHostState(),
            onSelectTab = {}, onPrimaryHero = {}, onAttendanceClick = {},
            onClients = {}, onTourPlans = {}, onVisits = {},
            onExpenses = {}, onSamples = {}, onEdetail = {}, onRcpa = {},
            onMore = {}, onLogout = {},
        )
    }
}

@Preview(name = "Home — not punched in", showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun HomePreviewFresh() {
    FieldPharmaTheme {
        HomeScaffold(
            state = HomeState(
                firstName = "Amit",
                greeting = "Good morning",
                attendance = AttendanceState.NotPunched,
                callsDone = 0, callsPlanned = 0,
                nextStop = null, upcomingStops = emptyList(),
                pendingSyncCount = 0, pendingSamples = 0, pendingExpenses = 0,
            ),
            billing = BillingSnapshot(BillingStatus.ACTIVE, null, null, 0L),
            loadingPlan = false,
            selectedTab = BottomTab.Home,
            snackbarHost = SnackbarHostState(),
            onSelectTab = {}, onPrimaryHero = {}, onAttendanceClick = {},
            onClients = {}, onTourPlans = {}, onVisits = {},
            onExpenses = {}, onSamples = {}, onEdetail = {}, onRcpa = {},
            onMore = {}, onLogout = {},
        )
    }
}
