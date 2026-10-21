package com.baseras.fieldpharma.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.ClientDto
import com.baseras.fieldpharma.data.remote.TourPlanEntryReq
import com.baseras.fieldpharma.data.remote.TourPlanReq
import com.baseras.fieldpharma.data.remote.UserRow
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import com.baseras.fieldpharma.ui.components.PillTone
import com.baseras.fieldpharma.ui.components.SectionHeader
import com.baseras.fieldpharma.ui.components.StatusPill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/* ─────── state ─────── */

data class AssignUiState(
    val loading: Boolean = true,
    val reports: List<UserRow> = emptyList(),
    val allClients: List<ClientDto> = emptyList(),
    val selectedUserId: String? = null,
    val weekStart: LocalDate = LocalDate.now(),
    /** per date (ISO), the list of chosen client ids in order */
    val plan: Map<String, List<String>> = emptyMap(),
    val busy: Boolean = false,
    val successCount: Int = 0,
    val error: String? = null,
)

class AssignPlanViewModel(private val api: Api) : ViewModel() {
    private val _s = MutableStateFlow(AssignUiState())
    val state = _s.asStateFlow()

    init { load() }

    fun load() {
        _s.value = _s.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val me = FieldPharmaApp.instance.authRepo.current
            runCatching {
                val users = api.listUsers().users
                val clients = api.listClients().clients
                users to clients
            }.onSuccess { (users, clients) ->
                val reports = users.filter {
                    it.isActive && it.role == "FIELD_PERSON" && it.managerId == me?.id
                }
                _s.value = _s.value.copy(
                    loading = false,
                    reports = reports,
                    allClients = clients,
                    selectedUserId = reports.firstOrNull()?.id,
                )
            }.onFailure { _s.value = _s.value.copy(loading = false, error = it.message) }
        }
    }

    fun selectUser(id: String) {
        _s.value = _s.value.copy(selectedUserId = id, plan = emptyMap())
    }

    fun shiftWeek(delta: Long) {
        _s.value = _s.value.copy(weekStart = _s.value.weekStart.plusDays(delta))
    }

    fun addClient(dateIso: String, clientId: String) {
        val current = _s.value.plan[dateIso].orEmpty()
        if (clientId in current) return
        _s.value = _s.value.copy(plan = _s.value.plan + (dateIso to (current + clientId)))
    }

    fun addClients(dateIso: String, clientIds: List<String>) {
        if (clientIds.isEmpty()) return
        val current = _s.value.plan[dateIso].orEmpty()
        val merged = current + clientIds.filter { it !in current }
        _s.value = _s.value.copy(plan = _s.value.plan + (dateIso to merged))
    }

    fun removeClient(dateIso: String, clientId: String) {
        val current = _s.value.plan[dateIso].orEmpty()
        val next = current.filter { it != clientId }
        _s.value = _s.value.copy(plan = _s.value.plan + (dateIso to next))
    }

    fun submit(onDone: () -> Unit) {
        val s = _s.value
        val uid = s.selectedUserId ?: return
        val days = s.plan.filter { it.value.isNotEmpty() }
        if (days.isEmpty()) {
            _s.value = s.copy(error = "Add at least one client to one day")
            return
        }
        _s.value = s.copy(busy = true, error = null, successCount = 0)
        viewModelScope.launch {
            var ok = 0
            for ((dateIso, clientIds) in days) {
                runCatching {
                    api.createTourPlan(TourPlanReq(
                        userId = uid,
                        date = dateIso,
                        entries = clientIds.map { TourPlanEntryReq(clientId = it) },
                    ))
                }.onSuccess { ok++ }
            }
            _s.value = _s.value.copy(busy = false, successCount = ok)
            if (ok == days.size) onDone()
        }
    }
}

/* ─────── Screen ─────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPlanScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: AssignPlanViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(AssignPlanViewModel::class) { AssignPlanViewModel(app.api) }
    })
    val s by vm.state.collectAsState()

    var dayToAdd by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Assign tour plan") },
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
            when {
                s.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                s.reports.isEmpty() -> EmptyReports()
                else -> {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SectionHeader("Field person")
                        FieldPersonPicker(s.reports, s.selectedUserId, vm::selectUser)

                        Spacer(Modifier.height(6.dp))
                        SectionHeader("Week")
                        WeekShifter(s.weekStart, vm::shiftWeek)

                        for (i in 0..6) {
                            val d = s.weekStart.plusDays(i.toLong())
                            val iso = d.toString()
                            val stops = (s.plan[iso].orEmpty())
                                .mapNotNull { id -> s.allClients.firstOrNull { it.id == id } }
                            DayCard(
                                date = d,
                                stops = stops,
                                onAdd = { dayToAdd = iso },
                                onRemove = { vm.removeClient(iso, it.id) },
                            )
                        }

                        s.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (s.successCount > 0) {
                            Text(
                                "Saved ${s.successCount} day${if (s.successCount == 1) "" else "s"}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.submit(onBack) },
                            enabled = !s.busy && s.plan.values.any { it.isNotEmpty() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(if (s.busy) "Saving…" else "Assign plan")
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            LoadingOverlay(visible = s.busy, label = "Saving plans…")
        }
    }

    // Add-clients dialog with pincode suggestions.
    dayToAdd?.let { iso ->
        AddClientsDialog(
            allClients = s.allClients,
            alreadySelected = s.plan[iso].orEmpty(),
            onDismiss = { dayToAdd = null },
            onPicked = { picked, extraFromPincode ->
                vm.addClients(iso, picked)
                if (extraFromPincode.isNotEmpty()) vm.addClients(iso, extraFromPincode)
                dayToAdd = null
            },
        )
    }
}

/* ─────── pieces ─────── */

@Composable
private fun FieldPersonPicker(reports: List<UserRow>, selectedId: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reports, key = { it.id }) { rep ->
            val isSelected = rep.id == selectedId
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                onClick = { onPick(rep.id) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = { onPick(rep.id) })
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(rep.name, style = MaterialTheme.typography.titleSmall)
                        rep.employeeCode?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekShifter(start: LocalDate, onShift: (Long) -> Unit) {
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { onShift(-7) }) { Text("← Previous") }
        Text(
            "${start.format(fmt)} — ${end.format(fmt)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = { onShift(7) }) { Text("Next →") }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    stops: List<ClientDto>,
    onAdd: () -> Unit,
    onRemove: (ClientDto) -> Unit,
) {
    val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(dayFmt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add client")
                }
            }
            if (stops.isEmpty()) {
                Text("No stops", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stops.forEachIndexed { idx, c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}. ${c.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            c.pincode?.let {
                                StatusPill(it, PillTone.Neutral)
                                Spacer(Modifier.width(6.dp))
                            }
                            IconButton(onClick = { onRemove(c) }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReports() {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("No field persons report to you", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask the admin to assign Field Persons under your account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ─────── Add-clients dialog with pincode suggestions ─────── */

@Composable
private fun AddClientsDialog(
    allClients: List<ClientDto>,
    alreadySelected: List<String>,
    onDismiss: () -> Unit,
    onPicked: (picked: List<String>, extraFromPincode: List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    val available = remember(allClients, alreadySelected, query) {
        allClients
            .filter { it.id !in alreadySelected }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name }
    }
    // Suggestion: other clients in the same pincode(s) as the picked ones.
    val suggested = remember(picked, allClients, alreadySelected) {
        val pins = picked.mapNotNull { id -> allClients.firstOrNull { it.id == id }?.pincode }.toSet()
        if (pins.isEmpty()) emptyList()
        else allClients.filter {
            !it.pincode.isNullOrBlank() && it.pincode in pins &&
                it.id !in picked && it.id !in alreadySelected
        }.sortedBy { it.name }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Add clients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search client") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(available, key = { it.id }) { c ->
                        val checked = c.id in picked
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                picked = if (checked) picked - c.id else picked + c.id
                            },
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    picked = if (it) picked + c.id else picked - c.id
                                })
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        buildString {
                                            append(c.type)
                                            c.city?.let { append(" · $it") }
                                            c.pincode?.let { append(" · $it") }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (suggested.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Also in same pincode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(suggested.take(10), key = { it.id }) { c ->
                            AssistChip(
                                onClick = { picked = picked + c.id },
                                label = { Text("+ ${c.name}") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { onPicked(picked.toList(), emptyList()) },
                        enabled = picked.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Add ${picked.size}")
                    }
                }
            }
        }
    }
}
