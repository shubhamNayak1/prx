package com.baseras.fieldpharma.ui.approvals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.ExpenseDto
import com.baseras.fieldpharma.data.remote.ExpenseReviewReq
import com.baseras.fieldpharma.data.remote.JointReviewReq
import com.baseras.fieldpharma.data.remote.TourPlanDto
import com.baseras.fieldpharma.data.remote.TourPlanReviewReq
import com.baseras.fieldpharma.data.remote.VisitDto
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ApprovalFilter(val label: String) {
    All("All"),
    TourPlans("Tour plans"),
    Expenses("Expenses"),
    JointWork("Joint work"),
}

data class ApprovalsUiState(
    val loading: Boolean = true,
    val tourPlans: List<TourPlanDto> = emptyList(),
    val expenses: List<ExpenseDto> = emptyList(),
    val jointVisits: List<VisitDto> = emptyList(),
    val filter: ApprovalFilter = ApprovalFilter.All,
    val actingId: String? = null,
    val error: String? = null,
)

class ApprovalsViewModel(private val api: Api) : ViewModel() {
    private val _state = MutableStateFlow(ApprovalsUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun setFilter(f: ApprovalFilter) { _state.value = _state.value.copy(filter = f) }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val plans = runCatching { api.tourPlans() }.getOrNull()?.plans.orEmpty()
                    .filter { it.status == "PLANNED" }
                val exps = runCatching { api.listExpenses(status = "PENDING") }.getOrNull()?.expenses.orEmpty()
                val jointVisits = runCatching { api.listPendingJointWork() }.getOrNull()?.visits.orEmpty()
                _state.value = _state.value.copy(
                    loading = false,
                    tourPlans = plans,
                    expenses = exps,
                    jointVisits = jointVisits,
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    fun reviewPlan(id: String, approve: Boolean) {
        _state.value = _state.value.copy(actingId = id)
        viewModelScope.launch {
            runCatching {
                api.reviewTourPlan(id, TourPlanReviewReq(status = if (approve) "APPROVED" else "REJECTED"))
            }
            _state.value = _state.value.copy(actingId = null)
            refresh()
        }
    }

    fun reviewExpense(id: String, approve: Boolean) {
        _state.value = _state.value.copy(actingId = id)
        viewModelScope.launch {
            runCatching {
                api.reviewExpense(id, ExpenseReviewReq(status = if (approve) "APPROVED" else "REJECTED"))
            }
            _state.value = _state.value.copy(actingId = null)
            refresh()
        }
    }

    fun reviewJointWork(id: String, approve: Boolean) {
        _state.value = _state.value.copy(actingId = id)
        viewModelScope.launch {
            runCatching { api.reviewJointWork(id, JointReviewReq(approved = approve)) }
            _state.value = _state.value.copy(actingId = null)
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    onBack: () -> Unit,
    initialFilter: ApprovalFilter = ApprovalFilter.All,
) {
    val app = FieldPharmaApp.instance
    val vm: ApprovalsViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(ApprovalsViewModel::class) { ApprovalsViewModel(app.api) }
    })
    val state by vm.state.collectAsState()
    LaunchedEffect(initialFilter) {
        if (initialFilter != ApprovalFilter.All) vm.setFilter(initialFilter)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Approvals", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            FilterChipRow(state.filter) { vm.setFilter(it) }
            Spacer(Modifier.height(12.dp))

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> ErrorBox(state.error!!) { vm.refresh() }
                else -> {
                    val f = state.filter
                    val plans = if (f == ApprovalFilter.All || f == ApprovalFilter.TourPlans) state.tourPlans else emptyList()
                    val exps = if (f == ApprovalFilter.All || f == ApprovalFilter.Expenses) state.expenses else emptyList()
                    val joints = if (f == ApprovalFilter.All || f == ApprovalFilter.JointWork) state.jointVisits else emptyList()
                    val total = plans.size + exps.size + joints.size

                    if (total == 0) {
                        EmptyApprovals()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                        ) {
                            if (plans.isNotEmpty()) {
                                item { SectionHeader("Tour plans · ${plans.size}") }
                                items(plans, key = { it.id }) { p ->
                                    TourPlanCard(
                                        plan = p,
                                        acting = state.actingId == p.id,
                                        onApprove = { vm.reviewPlan(p.id, true) },
                                        onReject = { vm.reviewPlan(p.id, false) },
                                    )
                                }
                            }
                            if (exps.isNotEmpty()) {
                                item { SectionHeader("Expenses · ${exps.size}") }
                                items(exps, key = { it.id }) { e ->
                                    ExpenseCard(
                                        expense = e,
                                        acting = state.actingId == e.id,
                                        onApprove = { vm.reviewExpense(e.id, true) },
                                        onReject = { vm.reviewExpense(e.id, false) },
                                    )
                                }
                            }
                            if (joints.isNotEmpty()) {
                                item { SectionHeader("Joint work · ${joints.size}") }
                                items(joints, key = { it.id }) { v ->
                                    JointVisitCard(
                                        visit = v,
                                        acting = state.actingId == v.id,
                                        onApprove = { vm.reviewJointWork(v.id, true) },
                                        onReject = { vm.reviewJointWork(v.id, false) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ─────── pieces ─────── */

@Composable
private fun FilterChipRow(current: ApprovalFilter, onPick: (ApprovalFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ApprovalFilter.values().forEach { f ->
            FilterChip(
                selected = f == current,
                onClick = { onPick(f) },
                label = { Text(f.label, style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun TourPlanCard(
    plan: TourPlanDto,
    acting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val stops = plan.entries.size
    ApprovalCard(
        icon = Icons.Default.CalendarMonth,
        title = plan.user?.name ?: "Unknown",
        subtitle = "${plan.date} · $stops stop${if (stops == 1) "" else "s"}",
        body = plan.notes,
        acting = acting,
        onApprove = onApprove,
        onReject = onReject,
    )
}

@Composable
private fun JointVisitCard(
    visit: VisitDto,
    acting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val repName = visit.user?.name ?: "A field person"
    val clientName = visit.client?.name ?: "a client"
    val when_ = visit.checkInAt.take(10)
    ApprovalCard(
        icon = Icons.Default.CheckCircle,
        title = "$repName @ $clientName",
        subtitle = "$when_  ·  claimed you were on this visit",
        body = visit.notes,
        acting = acting,
        onApprove = onApprove,
        onReject = onReject,
    )
}

@Composable
private fun ExpenseCard(
    expense: ExpenseDto,
    acting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val detail = buildString {
        append("₹${"%.0f".format(expense.amount)}")
        append(" · ")
        append(expense.type)
        if (!expense.category.isNullOrBlank()) { append(" · "); append(expense.category) }
        if (expense.distanceKm != null) { append(" · "); append("${expense.distanceKm} km") }
    }
    ApprovalCard(
        icon = Icons.Default.Receipt,
        title = expense.user?.name ?: "Unknown",
        subtitle = "${expense.date} · $detail",
        body = expense.remarks,
        acting = acting,
        onApprove = onApprove,
        onReject = onReject,
    )
}

@Composable
private fun ApprovalCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    body: String?,
    acting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill("Pending", PillTone.Warn)
            }
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !acting,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reject", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onApprove,
                    enabled = !acting,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (acting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyApprovals() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("All clear", style = MaterialTheme.typography.titleLarge)
        Text(
            "No pending approvals right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBox(msg: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/* ─────── preview ─────── */

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 900)
@Composable
private fun ApprovalsScreenPreview() {
    FieldPharmaTheme {
        Column(Modifier.padding(16.dp)) {
            FilterChipRow(ApprovalFilter.All) {}
            Spacer(Modifier.height(16.dp))
            SectionHeader("Tour plans · 2")
            Spacer(Modifier.height(8.dp))
            ApprovalCard(
                icon = Icons.Default.CalendarMonth,
                title = "Amit Singh",
                subtitle = "2026-10-21 · 8 stops",
                body = "Cardio focus week — covering North Delhi beat.",
                acting = false,
                onApprove = {}, onReject = {},
            )
            Spacer(Modifier.height(10.dp))
            SectionHeader("Expenses · 1")
            Spacer(Modifier.height(8.dp))
            ApprovalCard(
                icon = Icons.Default.Receipt,
                title = "Priya Patel",
                subtitle = "2026-10-20 · ₹480 · TA · 24 km",
                body = null,
                acting = false,
                onApprove = {}, onReject = {},
            )
        }
    }
}
