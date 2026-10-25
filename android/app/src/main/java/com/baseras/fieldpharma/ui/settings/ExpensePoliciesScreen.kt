package com.baseras.fieldpharma.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.ExpensePolicyDto
import com.baseras.fieldpharma.data.remote.ExpensePolicyUpsertReq
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import com.baseras.fieldpharma.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PoliciesUi(
    val loading: Boolean = true,
    val policies: List<ExpensePolicyDto> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class ExpensePoliciesViewModel(private val api: Api) : ViewModel() {
    private val _s = MutableStateFlow(PoliciesUi())
    val state = _s.asStateFlow()

    init { load() }

    fun load() {
        _s.value = _s.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { api.listExpensePolicies().policies }
                .onSuccess { _s.value = _s.value.copy(loading = false, policies = it) }
                .onFailure { _s.value = _s.value.copy(loading = false, error = it.message) }
        }
    }

    fun save(grade: String, ta: Double, da: Double, onDone: () -> Unit) {
        _s.value = _s.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching {
                api.upsertExpensePolicy(ExpensePolicyUpsertReq(grade = grade, taRatePerKm = ta, daFlatRate = da))
            }.onSuccess {
                _s.value = _s.value.copy(busy = false)
                onDone()
                load()
            }.onFailure {
                _s.value = _s.value.copy(busy = false, error = it.message ?: "Could not save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensePoliciesScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: ExpensePoliciesViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(ExpensePoliciesViewModel::class) { ExpensePoliciesViewModel(app.api) }
    })
    val s by vm.state.collectAsState()

    var editing by remember { mutableStateOf<ExpensePolicyDto?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("TA / DA rates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add grade") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                s.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                s.error != null -> ErrorRow(s.error!!) { vm.load() }
                s.policies.isEmpty() -> EmptyPolicies()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SectionHeader("Per-grade rates")
                    }
                    item {
                        Text(
                            "These apply to any Field Person with the matching grade. TA is per kilometre; DA is a flat daily amount.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(s.policies, key = { it.id }) { p ->
                        PolicyRow(p) { editing = p }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
            LoadingOverlay(visible = s.busy)
        }
    }

    if (creating) {
        PolicyDialog(
            initial = null,
            busy = s.busy,
            onDismiss = { creating = false },
            onSave = { grade, ta, da ->
                vm.save(grade, ta, da) { creating = false }
            },
        )
    }
    editing?.let { p ->
        PolicyDialog(
            initial = p,
            busy = s.busy,
            onDismiss = { editing = null },
            onSave = { _, ta, da -> vm.save(p.grade, ta, da) { editing = null } },
        )
    }
}

@Composable
private fun PolicyRow(p: ExpensePolicyDto, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onEdit,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Grade ${p.grade}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "TA ₹${formatRate(p.taRatePerKm)}/km · DA ₹${formatRate(p.daFlatRate)}/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPolicies() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Receipt,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("No rates set yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap Add grade to set TA and DA for your field grades (A, B, C …).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorRow(msg: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Could not load", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun PolicyDialog(
    initial: ExpensePolicyDto?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (grade: String, ta: Double, da: Double) -> Unit,
) {
    var grade by remember { mutableStateOf(initial?.grade ?: "") }
    var ta by remember { mutableStateOf(initial?.taRatePerKm?.toString() ?: "") }
    var da by remember { mutableStateOf(initial?.daFlatRate?.toString() ?: "") }
    val isEdit = initial != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isEdit) "Edit grade ${initial!!.grade}" else "Add grade",
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = grade,
                    onValueChange = { v -> grade = v.take(8).uppercase() },
                    label = { Text("Grade (A, B, C …)") },
                    enabled = !isEdit,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ta,
                    onValueChange = { ta = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("TA per km (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = da,
                    onValueChange = { da = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("DA per day (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = !busy && grade.isNotBlank() && ta.toDoubleOrNull() != null && da.toDoubleOrNull() != null,
                        onClick = { onSave(grade.trim(), ta.toDouble(), da.toDouble()) },
                    ) { Text("Save") }
                }
            }
        }
    }
}

private fun formatRate(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
