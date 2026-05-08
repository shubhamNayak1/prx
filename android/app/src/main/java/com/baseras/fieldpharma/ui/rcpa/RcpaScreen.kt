package com.baseras.fieldpharma.ui.rcpa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.RcpaDto
import com.baseras.fieldpharma.data.repo.RcpaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RcpaListViewModel(private val repo: RcpaRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<RcpaDto>>(emptyList())
    val items = _items.asStateFlow()
    val loading = MutableStateFlow(false)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            repo.list().onSuccess { _items.value = it }
            loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcpaScreen(onBack: () -> Unit, onNew: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: RcpaListViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(RcpaListViewModel::class) { RcpaListViewModel(app.rcpaRepo) }
    })
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RCPA") },
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
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "New RCPA")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (items.isEmpty() && !loading) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No RCPA entries yet. Tap + to capture competitor data at a chemist.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { e ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(e.client?.name ?: "—", style = MaterialTheme.typography.titleMedium)
                                    Text(e.date.take(10), style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("Ours: ${e.ourBrand}"); Text("${e.ourQuantity} units", style = MaterialTheme.typography.titleMedium) }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Comp: ${e.competitorBrand}")
                                        Text("${e.competitorQuantity} units", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                e.remarks?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
