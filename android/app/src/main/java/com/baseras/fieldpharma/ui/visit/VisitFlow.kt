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
    private val api: com.baseras.fieldpharma.data.remote.Api,
) : ViewModel() {
    val activeVisit = MutableStateFlow<VisitEntity?>(null)
    val activeClient = MutableStateFlow<ClientEntity?>(null)

    val recent = visitRepo.observeRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val msg = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    val managers = MutableStateFlow<List<com.baseras.fieldpharma.data.remote.UserRow>>(emptyList())

    init {
        viewModelScope.launch {
            visitRepo.active()?.let {
                activeVisit.value = it
                activeClient.value = clientRepo.byId(it.clientId)
            }
            // Load managers once for the joint-work picker. Field-role users
            // can still see managers above them because /api/users returns
            // the company list filtered by role on the server.
            runCatching { api.listUsers() }.onSuccess { res ->
                managers.value = res.users.filter {
                    it.isActive && it.role != "FIELD_PERSON"
                }
            }
        }
    }

    fun checkIn(client: ClientEntity) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching {
                val geo = location.current()
                val localId = visitRepo.checkIn(client.id, geo?.lat, geo?.lng)
                activeVisit.value = visitRepo.byId(localId)
                activeClient.value = client
                msg.value = "Checked in at ${client.name}"
            }.onFailure { msg.value = "Check-in failed: ${it.message}" }
            busy.value = false
        }
    }

    fun saveNotes(notes: String, products: String) {
        val v = activeVisit.value ?: return
        viewModelScope.launch {
            runCatching {
                val list = products.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                visitRepo.saveNotes(v.localId, notes, list)
                activeVisit.value = visitRepo.byId(v.localId)
            }.onFailure { msg.value = "Save failed: ${it.message}" }
        }
    }

    /** Persists current field edits AND checks out. busy always resets on failure. */
    fun checkOutWith(notes: String, products: String, jointWithUserId: String?) {
        val v = activeVisit.value ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching {
                val list = products.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                visitRepo.saveNotes(v.localId, notes, list)
                val geo = location.current()
                visitRepo.checkOut(v.localId, geo?.lat, geo?.lng, jointWithUserId)
                activeVisit.value = null
                activeClient.value = null
                msg.value = if (jointWithUserId != null)
                    "Visit completed — joint-work claim sent for approval"
                else "Visit completed and queued for sync"
            }.onFailure { msg.value = "Check-out failed: ${it.message}" }
            busy.value = false
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
            VisitViewModel(app.visitRepo, app.clientRepo, app.locationProvider, app.api)
        }
    })
    val visit by vm.activeVisit.collectAsState()
    val client by vm.activeClient.collectAsState()
    val recent by vm.recent.collectAsState()
    val msg by vm.msg.collectAsState()
    val busy by vm.busy.collectAsState()
    val managers by vm.managers.collectAsState()

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
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (visit != null) {
                ActiveVisitPanel(
                    visit = visit!!,
                    client = client,
                    busy = busy,
                    managers = managers,
                    onSave = vm::saveNotes,
                    onCheckOut = vm::checkOutWith,
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
            com.baseras.fieldpharma.ui.components.LoadingOverlay(
                visible = busy,
                label = if (visit != null) "Checking out…" else "Starting visit…",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveVisitPanel(
    visit: VisitEntity,
    client: ClientEntity?,
    busy: Boolean,
    managers: List<com.baseras.fieldpharma.data.remote.UserRow>,
    onSave: (notes: String, products: String) -> Unit,
    onCheckOut: (notes: String, products: String, jointWithUserId: String?) -> Unit,
) {
    var notes by remember(visit.localId) { mutableStateOf(visit.notes ?: "") }
    var products by remember(visit.localId) { mutableStateOf(visit.productsJson?.replace("|", ", ") ?: "") }

    // Joint-work local state
    var jointWork by remember(visit.localId) { mutableStateOf(false) }
    var jointWithId by remember(visit.localId) { mutableStateOf<String?>(null) }
    var mgrMenu by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            // Joint-work toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = jointWork, onCheckedChange = { jointWork = it; if (!it) jointWithId = null })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Joint working", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Mark if a manager was with you — they'll approve this visit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (jointWork) {
                ExposedDropdownMenuBox(expanded = mgrMenu, onExpandedChange = { mgrMenu = it }) {
                    val selected = managers.firstOrNull { it.id == jointWithId }
                    OutlinedTextField(
                        value = selected?.let { "${it.name} (${roleShort(it.role)})" } ?: "Pick manager",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Manager with you") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mgrMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = mgrMenu, onDismissRequest = { mgrMenu = false }) {
                        if (managers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No managers found", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {},
                            )
                        }
                        managers.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.name}  ·  ${roleShort(m.role)}") },
                                onClick = { jointWithId = m.id; mgrMenu = false },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSave(notes, products) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                Button(
                    onClick = {
                        val jwUser = if (jointWork) jointWithId else null
                        onCheckOut(notes, products, jwUser)
                    },
                    enabled = !busy && (!jointWork || jointWithId != null),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Check out")
                    }
                }
            }
        }
    }
}

private fun roleShort(role: String): String = when (role) {
    "ADMIN" -> "Admin"
    "SENIOR_MANAGER" -> "SM"
    "REGIONAL_MANAGER" -> "RM"
    else -> role
}
