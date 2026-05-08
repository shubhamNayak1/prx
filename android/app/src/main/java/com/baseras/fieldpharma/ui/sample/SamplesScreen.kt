package com.baseras.fieldpharma.ui.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.SampleBalanceDto
import com.baseras.fieldpharma.data.repo.SampleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SamplesViewModel(
    private val repo: SampleRepository,
    private val location: com.baseras.fieldpharma.location.LocationProvider,
) : ViewModel() {
    private val _balance = MutableStateFlow<List<SampleBalanceDto>>(emptyList())
    val balance = _balance.asStateFlow()
    val loading = MutableStateFlow(false)
    val msg = MutableStateFlow<String?>(null)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            repo.balance().onSuccess { _balance.value = it }
            loading.value = false
        }
    }

    fun distribute(issueId: String, qty: Int) {
        viewModelScope.launch {
            val geo = location.current()
            repo.distribute(issueId, qty, lat = geo?.lat, lng = geo?.lng).onSuccess {
                msg.value = "Recorded $qty unit(s)"
                refresh()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplesScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: SamplesViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(SamplesViewModel::class) { SamplesViewModel(app.sampleRepo, app.locationProvider) }
    })
    val balance by vm.balance.collectAsState()
    val loading by vm.loading.collectAsState()
    val msg by vm.msg.collectAsState()
    var distributing by remember { mutableStateOf<SampleBalanceDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Samples & gifts") },
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
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            msg?.let { Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (balance.isEmpty() && !loading) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No samples issued to you yet. Ask your manager to issue stock.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(balance, key = { it.issueId }) { b ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(b.product.name, style = MaterialTheme.typography.titleMedium)
                                Text("Unit: ${b.product.unitType}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Issued: ${b.issued}")
                                    Text("Used: ${b.distributed}")
                                    Text("Left: ${b.remaining}", style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { distributing = b },
                                    enabled = b.remaining > 0,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (b.remaining > 0) "Distribute" else "No stock left") }
                            }
                        }
                    }
                }
            }
        }
    }

    distributing?.let { item ->
        DistributeDialog(
            balance = item,
            onDismiss = { distributing = null },
            onConfirm = { qty ->
                vm.distribute(item.issueId, qty)
                distributing = null
            },
        )
    }
}

@Composable
private fun DistributeDialog(
    balance: SampleBalanceDto,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var qty by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Distribute ${balance.product.name}") },
        text = {
            Column {
                Text("Available: ${balance.remaining} ${balance.product.unitType}(s)")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = qty.toIntOrNull() ?: 0
                    if (n in 1..balance.remaining) onConfirm(n)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
