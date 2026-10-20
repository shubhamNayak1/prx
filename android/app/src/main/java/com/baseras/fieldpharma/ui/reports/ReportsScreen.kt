package com.baseras.fieldpharma.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.RoleKpis
import com.baseras.fieldpharma.data.remote.TeamRollup
import com.baseras.fieldpharma.data.repo.DashboardRepository
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* ─────── State ─────── */

data class ReportsUiState(
    val loading: Boolean = true,
    val role: String = "",
    val kpis: RoleKpis? = null,
    val team: List<TeamRollup> = emptyList(),
    val error: String? = null,
)

class ReportsViewModel(private val repo: DashboardRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state = _state.asStateFlow()
    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.roleSummary()
                .onSuccess { _state.value = ReportsUiState(false, it.role, it.kpis, it.team) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

/* ─────── Screen ─────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: ReportsViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(ReportsViewModel::class) { ReportsViewModel(app.dashboardRepo) }
    })
    val s by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (s.error != null) {
                Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { AttainmentDonut(s.kpis?.attainmentPct ?: 0) }
                    item { KpiStrip(s.kpis) }
                    item {
                        SectionHeader(
                            when (s.role) {
                                "ADMIN" -> "Attainment by region"
                                "SENIOR_MANAGER" -> "Attainment by Regional Manager"
                                "REGIONAL_MANAGER" -> "Attainment by Field Person"
                                else -> "Attainment"
                            }
                        )
                    }
                    item { AttainmentBarChart(s.team) }
                    item { SectionHeader("Active today") }
                    item { ActiveTodayBars(s.team) }
                }
            }
        }
    }
}

/* ─────── Charts ─────── */

@Composable
private fun AttainmentDonut(pct: Int) {
    val sweep = (pct.coerceIn(0, 100) / 100f) * 360f
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val variant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 18f
                    val padding = stroke / 2
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(padding, padding),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(padding, padding),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("attainment", style = MaterialTheme.typography.labelSmall, color = variant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("This month", style = MaterialTheme.typography.labelMedium, color = variant)
                Text(
                    "Monthly plan vs calls completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface,
                )
                Spacer(Modifier.height(10.dp))
                val tone = when {
                    pct >= 80 -> PillTone.Success
                    pct >= 50 -> PillTone.Warn
                    else -> PillTone.Error
                }
                StatusPill(
                    when (tone) {
                        PillTone.Success -> "On track"
                        PillTone.Warn -> "At risk"
                        else -> "Below target"
                    },
                    tone,
                )
            }
        }
    }
}

@Composable
private fun KpiStrip(k: RoleKpis?) {
    if (k == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiTile(
            label = "Calls MTD", value = k.monthVisits.toString(),
            sub = "of ${k.monthPlanned} planned", modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "Active today", value = "${k.activeToday}",
            sub = "field persons", modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "Pending exp.", value = k.pendingExpensesCount.toString(),
            sub = "awaiting review", modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AttainmentBarChart(team: List<TeamRollup>) {
    if (team.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                "No sub-teams to chart",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }

    val maxPct = (team.maxOf { it.attainmentPct }).coerceAtLeast(100)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            team.forEach { t ->
                Column {
                    Row {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${t.attainmentPct}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Canvas(
                        Modifier.fillMaxWidth().height(10.dp),
                    ) {
                        drawRoundRect(
                            color = track,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
                        )
                        val w = size.width * (t.attainmentPct.toFloat() / maxPct)
                        drawRoundRect(
                            color = primary,
                            size = Size(w, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTodayBars(team: List<TeamRollup>) {
    if (team.isEmpty()) return
    val maxReps = (team.maxOf { it.totalReps.coerceAtLeast(1) })
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth().height(170.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            team.forEach { t ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${t.activeToday}/${t.totalReps}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    val total = t.totalReps.coerceAtLeast(1)
                    val activeH = (t.activeToday.toFloat() / total)
                    Canvas(
                        Modifier.fillMaxWidth().height(100.dp * (total.toFloat() / maxReps.coerceAtLeast(1))),
                    ) {
                        drawRoundRect(
                            color = inactive,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                        )
                        drawRoundRect(
                            color = active,
                            topLeft = Offset(0f, size.height * (1f - activeH)),
                            size = Size(size.width, size.height * activeH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t.name.split(" ").first(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1100)
@Composable
private fun ReportsPreview() {
    FieldPharmaTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AttainmentDonut(72)
            AttainmentBarChart(listOf(
                TeamRollup("1", "Suresh Sharma", "REGIONAL_MANAGER", "ASM", 8, 10, 42, 120, 420, 580, 72, "ActiveToday"),
                TeamRollup("2", "Priya Nair", "REGIONAL_MANAGER", "ASM", 11, 12, 60, 110, 500, 640, 78, "ActiveToday"),
                TeamRollup("3", "Vikram Shah", "REGIONAL_MANAGER", "ASM", 10, 12, 48, 108, 480, 700, 69, "ActiveToday"),
            ))
            ActiveTodayBars(listOf(
                TeamRollup("1", "Suresh Sharma", "REGIONAL_MANAGER", "ASM", 8, 10, 42, 120, 420, 580, 72, "ActiveToday"),
                TeamRollup("2", "Priya Nair", "REGIONAL_MANAGER", "ASM", 11, 12, 60, 110, 500, 640, 78, "ActiveToday"),
                TeamRollup("3", "Vikram Shah", "REGIONAL_MANAGER", "ASM", 10, 12, 48, 108, 480, 700, 69, "ActiveToday"),
            ))
        }
    }
}
