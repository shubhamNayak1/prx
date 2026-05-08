package com.baseras.fieldpharma.ui.visit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.local.ClientEntity
import com.baseras.fieldpharma.data.local.VisitEntity
import com.baseras.fieldpharma.data.repo.ClientRepository
import com.baseras.fieldpharma.data.repo.VisitRepository
import com.baseras.fieldpharma.location.LocationProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VisitViewModel(
    private val visitRepo: VisitRepository,
    private val clientRepo: ClientRepository,
    private val location: LocationProvider,
) : ViewModel() {
    val activeVisit = MutableStateFlow<VisitEntity?>(null)
    val activeClient = MutableStateFlow<ClientEntity?>(null)

    val recent = visitRepo.observeRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val msg = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            visitRepo.active()?.let {
                activeVisit.value = it
                activeClient.value = clientRepo.byId(it.clientId)
            }
        }
    }

    fun checkIn(client: ClientEntity) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val geo = location.current()
            val localId = visitRepo.checkIn(client.id, geo?.lat, geo?.lng)
            activeVisit.value = visitRepo.byId(localId)
            activeClient.value = client
            busy.value = false
            msg.value = "Checked in at ${client.name}"
        }
    }

    fun saveNotes(notes: String, products: String) {
        val v = activeVisit.value ?: return
        viewModelScope.launch {
            val list = products.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            visitRepo.saveNotes(v.localId, notes, list)
            activeVisit.value = visitRepo.byId(v.localId)
        }
    }

    fun checkOut() {
        val v = activeVisit.value ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val geo = location.current()
            visitRepo.checkOut(v.localId, geo?.lat, geo?.lng)
            activeVisit.value = null
            activeClient.value = null
            busy.value = false
            msg.value = "Visit completed and queued for sync"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitFlow(
    checkInClientId: String? = null,
    onBack: () -> Unit,
    onPickClient: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val vm: VisitViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(VisitViewModel::class) {
            VisitViewModel(app.visitRepo, app.clientRepo, app.locationProvider)
        }
    })
    val visit by vm.activeVisit.collectAsState()
    val client by vm.activeClient.collectAsState()
    val recent by vm.recent.collectAsState()
    val msg by vm.msg.collectAsState()
    val busy by vm.busy.collectAsState()

    LaunchedEffect(checkInClientId) {
        if (checkInClientId != null && visit == null) {
            val c = app.clientRepo.byId(checkInClientId)
            if (c != null) vm.checkIn(c)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (visit != null) "Visit in progress" else "Visits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (visit != null) {
                ActiveVisitPanel(
                    visit = visit!!,
                    client = client,
                    busy = busy,
                    onSave = vm::saveNotes,
                    onCheckOut = vm::checkOut,
                )
            } else {
                Button(
                    onClick = onPickClient,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Start a visit — pick a client") }
            }

            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Text("Recent visits", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(recent, key = { it.localId }) { v ->
                    ListItem(
                        headlineContent = { Text(v.checkInAt.take(10)) },
                        supportingContent = {
                            val state = if (v.serverId != null) "Synced" else v.state
                            Text(buildString {
                                append("client: ${v.clientId.take(8)}…")
                                v.notes?.let { append(" • $it") }
                                append(" • $state")
                            })
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ActiveVisitPanel(
    visit: VisitEntity,
    client: ClientEntity?,
    busy: Boolean,
    onSave: (notes: String, products: String) -> Unit,
    onCheckOut: () -> Unit,
) {
    var notes by remember(visit.localId) { mutableStateOf(visit.notes ?: "") }
    var products by remember(visit.localId) { mutableStateOf(visit.productsJson?.replace("|", ", ") ?: "") }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(client?.name ?: "Client", style = MaterialTheme.typography.titleMedium)
            Text("Checked in: ${visit.checkInAt.take(19).replace("T", " ")}", style = MaterialTheme.typography.labelSmall)
            HorizontalDivider()

            OutlinedTextField(
                value = products, onValueChange = { products = it },
                label = { Text("Products discussed (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes / DCR") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSave(notes, products) },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                Button(
                    onClick = onCheckOut,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Check out") }
            }
        }
    }
}
