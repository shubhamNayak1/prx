package com.baseras.fieldpharma.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.local.ClientEntity
import com.baseras.fieldpharma.data.repo.ClientRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClientsViewModel(private val repo: ClientRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val clients = query
        .flatMapLatest { q -> if (q.isBlank()) repo.observeAll() else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init { refresh() }

    fun setQuery(q: String) { query.value = q }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            repo.refresh().onFailure { error.value = it.message }
            refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onClick: (ClientEntity) -> Unit,
) {
    val app = FieldPharmaApp.instance
    val vm: ClientsViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(ClientsViewModel::class) { ClientsViewModel(app.clientRepo) }
    })
    val items by vm.clients.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var q by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clients") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "Add client")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it; vm.setQuery(it) },
                label = { Text("Search by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (items.isEmpty() && !refreshing) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No clients yet — pull refresh, or tap + to add one.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { client ->
                        ListItem(
                            headlineContent = { Text(client.name) },
                            supportingContent = {
                                Text(buildString {
                                    append(client.type)
                                    client.speciality?.let { append(" • $it") }
                                    client.city?.let { append(" • $it") }
                                })
                            },
                            modifier = Modifier.clickable { onClick(client) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
