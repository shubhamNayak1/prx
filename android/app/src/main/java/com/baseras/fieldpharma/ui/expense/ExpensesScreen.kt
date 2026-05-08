package com.baseras.fieldpharma.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.ExpenseDto
import com.baseras.fieldpharma.data.repo.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpensesViewModel(private val repo: ExpenseRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<ExpenseDto>>(emptyList())
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
fun ExpensesScreen(onBack: () -> Unit, onNew: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: ExpensesViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(ExpensesViewModel::class) { ExpensesViewModel(app.expenseRepo) }
    })
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
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
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (items.isEmpty() && !loading) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No expenses claimed yet. Tap + to add one.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = {
                                Text("${e.type} • ₹${"%.2f".format(e.amount)}")
                            },
                            supportingContent = {
                                Text(buildString {
                                    append(e.date)
                                    e.fromLocation?.let { append(" • $it") }
                                    e.toLocation?.let { append(" → $it") }
                                    e.category?.let { append(" • $it") }
                                })
                            },
                            trailingContent = { StatusBadge(e.status) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "APPROVED" -> "Approved" to MaterialTheme.colorScheme.primary
        "REJECTED" -> "Rejected" to MaterialTheme.colorScheme.error
        else -> "Pending" to MaterialTheme.colorScheme.secondary
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}
