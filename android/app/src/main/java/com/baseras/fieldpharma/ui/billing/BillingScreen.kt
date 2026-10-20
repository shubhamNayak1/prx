package com.baseras.fieldpharma.ui.billing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.billing.BillingSnapshot
import com.baseras.fieldpharma.billing.BillingStatus
import com.baseras.fieldpharma.data.remote.InvoiceDto
import com.baseras.fieldpharma.data.repo.BillingRepository
import com.baseras.fieldpharma.data.repo.DashboardRepository
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Cycle(val label: String, val months: Int, val discountPct: Int) {
    MONTHLY("Monthly", 1, 0),
    QUARTERLY("Quarterly", 3, 10),
    ANNUAL("Annual", 12, 20),
}

private const val PRICE_PER_REP_PAISE = 49_900L
private const val GST_PCT = 18

data class BillingUiState(
    val loading: Boolean = true,
    val billableUsers: Int = 1,
    val billing: BillingSnapshot = BillingSnapshot.UNKNOWN,
    val invoices: List<InvoiceDto> = emptyList(),
    val selectedCycle: Cycle = Cycle.ANNUAL,
    val upgrading: Boolean = false,
    val error: String? = null,
)

class BillingViewModel(
    private val billingRepo: BillingRepository,
    private val dashboardRepo: DashboardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BillingUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun selectCycle(c: Cycle) { _state.value = _state.value.copy(selectedCycle = c) }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val summary = runCatching { dashboardRepo.roleSummary() }.getOrNull()?.getOrNull()
            val snap = runCatching { billingRepo.refresh() }.getOrNull()?.getOrNull()
            val invs = runCatching { billingRepo.invoices() }.getOrNull()?.getOrNull().orEmpty()
            _state.value = _state.value.copy(
                loading = false,
                billableUsers = (summary?.kpis?.billableUsers ?: 1).coerceAtLeast(1),
                billing = snap ?: billingRepo.snapshot,
                invoices = invs,
            )
        }
    }

    fun upgrade(onLink: (String) -> Unit) {
        _state.value = _state.value.copy(upgrading = true, error = null)
        viewModelScope.launch {
            billingRepo.upgrade(state.value.selectedCycle.name)
                .onSuccess { res ->
                    onLink(res.paymentLink)
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(upgrading = false, error = it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: BillingViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(BillingViewModel::class) {
            BillingViewModel(app.billingRepo, app.dashboardRepo)
        }
    })
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Billing", style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusStrip(state) }

            if (state.billing.status != BillingStatus.CANCELLED) {
                item {
                    PlanPicker(
                        selected = state.selectedCycle,
                        billableUsers = state.billableUsers,
                        onPick = vm::selectCycle,
                    )
                }
                item {
                    PricePreview(state.selectedCycle, state.billableUsers)
                }
                item {
                    val preview = priceFor(state.selectedCycle, state.billableUsers)
                    Button(
                        onClick = {
                            vm.upgrade { url ->
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.upgrading,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        if (state.upgrading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (state.billing.status) {
                                    BillingStatus.TRIAL, BillingStatus.LOCKED, BillingStatus.PAST_DUE ->
                                        "Pay ${formatInr(preview.totalPaise)} & activate"
                                    else -> "Switch & pay ${formatInr(preview.totalPaise)}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            if (state.invoices.isNotEmpty()) {
                item { SectionHeader("Invoices · ${state.invoices.size}") }
                items(state.invoices, key = { it.id }) { inv -> InvoiceRow(inv, ctx) }
            } else if (!state.loading) {
                item { SectionHeader("Invoices") }
                item {
                    Text(
                        "No invoices yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.error?.let { item { ErrorBanner(it) } }
        }
    }
}

/* ─────── pieces ─────── */

@Composable
private fun StatusStrip(state: BillingUiState) {
    val b = state.billing
    val sub = when (b.status) {
        BillingStatus.TRIAL -> b.trialDaysLeft?.let { "$it day${if (it == 1) "" else "s"} left" } ?: "Trial"
        BillingStatus.ACTIVE -> b.cycle ?: "Active"
        BillingStatus.LOCKED -> "Pay to restore access"
        BillingStatus.PAST_DUE -> "Payment overdue"
        BillingStatus.CANCELLED -> "Cancelled"
        BillingStatus.UNKNOWN -> "—"
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Subscription",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    b.status.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    text = sub,
                    tone = when (b.status) {
                        BillingStatus.ACTIVE -> PillTone.Success
                        BillingStatus.TRIAL -> if ((b.trialDaysLeft ?: 0) <= 2) PillTone.Error else PillTone.Warn
                        BillingStatus.LOCKED, BillingStatus.PAST_DUE -> PillTone.Error
                        else -> PillTone.Neutral
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                MiniStat("Billable", "${state.billableUsers}", "users", Modifier.weight(1f))
                MiniStat(
                    "List rate",
                    formatInr(PRICE_PER_REP_PAISE),
                    "/user/mo",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, sub: String, mod: Modifier = Modifier) {
    Column(mod) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanPicker(selected: Cycle, billableUsers: Int, onPick: (Cycle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Choose a cycle")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cycle.values().forEach { c ->
                PlanCard(
                    cycle = c,
                    billableUsers = billableUsers,
                    selected = c == selected,
                    onClick = { onPick(c) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    cycle: Cycle,
    billableUsers: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = PRICE_PER_REP_PAISE * (100 - cycle.discountPct) / 100
    val borderMod = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
    } else Modifier
    Surface(
        modifier = modifier.then(borderMod),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cycle.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (cycle.discountPct > 0) StatusPill("SAVE ${cycle.discountPct}%", PillTone.Success)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatInr(effective),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "/user/mo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PricePreview(cycle: Cycle, billableUsers: Int) {
    val p = priceFor(cycle, billableUsers)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Invoice preview", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${cycle.months} month${if (cycle.months == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            PriceRow(
                "$billableUsers users × ${formatInr(PRICE_PER_REP_PAISE)} × ${cycle.months}",
                formatInr(p.grossPaise),
            )
            if (cycle.discountPct > 0) {
                PriceRow(
                    "Cycle discount (${cycle.discountPct}%)",
                    "− ${formatInr(p.grossPaise - p.subtotalPaise)}",
                    discount = true,
                )
            }
            PriceRow("Subtotal", formatInr(p.subtotalPaise))
            PriceRow("GST ($GST_PCT%)", formatInr(p.gstPaise))
            Divider(Modifier.padding(vertical = 6.dp))
            Row {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(formatInr(p.totalPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, discount: Boolean = false) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (discount) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InvoiceRow(inv: InvoiceDto, ctx: android.content.Context) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inv.number, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${inv.periodStart.take(10)} → ${inv.periodEnd.take(10)} · ${inv.cycle.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatInr(inv.totalPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                when (inv.status) {
                    "PAID" -> StatusPill("Paid", PillTone.Success)
                    "SENT" -> StatusPill("Sent", PillTone.Info)
                    "OVERDUE" -> StatusPill("Overdue", PillTone.Error)
                    "DRAFT" -> StatusPill("Draft", PillTone.Neutral)
                    "VOID" -> StatusPill("Void", PillTone.Neutral)
                }
                if (!inv.pdfUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(inv.pdfUrl))) },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(msg: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/* ─────── pricing helpers (mirror backend/src/lib/billing.ts) ─────── */

private data class Breakdown(
    val grossPaise: Long,
    val subtotalPaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
)

private fun priceFor(cycle: Cycle, users: Int): Breakdown {
    val gross = PRICE_PER_REP_PAISE * users * cycle.months
    val sub = gross * (100 - cycle.discountPct) / 100
    val gst = sub * GST_PCT / 100
    return Breakdown(gross, sub, gst, sub + gst)
}

private fun formatInr(paise: Long): String {
    val rupees = paise / 100.0
    val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))
    formatter.maximumFractionDigits = if (rupees % 1.0 == 0.0) 0 else 2
    return formatter.format(rupees)
}

/* ─────── preview ─────── */

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1100)
@Composable
private fun BillingPreview() {
    FieldPharmaTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusStrip(BillingUiState(
                loading = false,
                billableUsers = 24,
                billing = BillingSnapshot(BillingStatus.TRIAL, cycle = null, trialEndsAt = null, fetchedAt = 0L),
            ))
            PlanPicker(selected = Cycle.ANNUAL, billableUsers = 24, onPick = {})
            PricePreview(Cycle.ANNUAL, billableUsers = 24)
        }
    }
}
