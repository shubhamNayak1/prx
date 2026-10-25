package com.baseras.fieldpharma.ui.dcr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.DcrDayDto
import com.baseras.fieldpharma.data.remote.DcrDayReq
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import com.baseras.fieldpharma.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DcrDayUi(
    val loading: Boolean = true,
    val date: String = LocalDate.now().toString(),
    val day: DcrDayDto? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val savedAt: Long? = null,
)

class DcrDayViewModel(private val api: Api) : ViewModel() {
    private val _s = MutableStateFlow(DcrDayUi())
    val state = _s.asStateFlow()

    init { load() }

    fun load() {
        _s.value = _s.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val d = _s.value.date
                api.listDcrDays(from = d, to = d).days.firstOrNull()
            }.onSuccess {
                _s.value = _s.value.copy(loading = false, day = it ?: DcrDayDto(date = _s.value.date))
            }.onFailure {
                _s.value = _s.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun save(req: DcrDayReq, onDone: () -> Unit) {
        _s.value = _s.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { api.upsertDcrDay(req) }
                .onSuccess {
                    _s.value = _s.value.copy(busy = false, day = it.day, savedAt = System.currentTimeMillis())
                    onDone()
                }
                .onFailure { _s.value = _s.value.copy(busy = false, error = it.message ?: "Save failed") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DcrDayScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: DcrDayViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(DcrDayViewModel::class) { DcrDayViewModel(app.api) }
    })
    val s by vm.state.collectAsState()

    var workTypeI by remember(s.day) { mutableStateOf(s.day?.workTypeI ?: "INDP") }
    var workTypeII by remember(s.day) { mutableStateOf(s.day?.workTypeII ?: "LOCAL") }
    var place by remember(s.day) { mutableStateOf(s.day?.placeOfWork ?: "") }
    var baseTown by remember(s.day) { mutableStateOf(s.day?.baseTownOnOs ?: "") }
    var routeNo by remember(s.day) { mutableStateOf(s.day?.routeNoInSvl ?: "") }
    var km by remember(s.day) { mutableStateOf(s.day?.kmTravelled?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Today's work header") },
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Fills in the DCR sheet header for ${s.date}. All fields are optional — anything you leave blank just prints blank in the export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SectionHeader("Type of work I")
                    SegmentedRow(
                        options = listOf("INDP", "JOINT", "MEETING"),
                        selected = workTypeI,
                        onSelect = { workTypeI = it },
                    )

                    SectionHeader("Type of work II")
                    SegmentedRow(
                        options = listOf("LOCAL", "EX", "OS"),
                        labels = listOf("Local", "Ex-station", "Out-station"),
                        selected = workTypeII,
                        onSelect = { workTypeII = it },
                    )

                    OutlinedTextField(
                        value = place, onValueChange = { place = it },
                        label = { Text("Place of work (area / market)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (workTypeII == "OS") {
                        OutlinedTextField(
                            value = baseTown, onValueChange = { baseTown = it },
                            label = { Text("Base town on OS work") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = routeNo, onValueChange = { routeNo = it },
                        label = { Text("Route no (SVL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = km,
                        onValueChange = { v -> km = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("Kilometres travelled") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !s.busy,
                        onClick = {
                            vm.save(
                                DcrDayReq(
                                    date = s.date,
                                    workTypeI = workTypeI,
                                    workTypeII = workTypeII,
                                    placeOfWork = place.ifBlank { null },
                                    baseTownOnOs = baseTown.ifBlank { null },
                                    routeNoInSvl = routeNo.ifBlank { null },
                                    kmTravelled = km.toDoubleOrNull(),
                                ),
                                onDone = onBack,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save") }

                    s.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            LoadingOverlay(visible = s.busy)
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<String>,
    labels: List<String> = options,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(labels.getOrElse(i) { opt }) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
