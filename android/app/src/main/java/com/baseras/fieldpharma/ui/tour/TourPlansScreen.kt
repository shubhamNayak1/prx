package com.baseras.fieldpharma.ui.tour

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.TourPlanDto
import com.baseras.fieldpharma.data.repo.TourPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TourPlansViewModel(private val repo: TourPlanRepository) : ViewModel() {
    private val _plans = MutableStateFlow<List<TourPlanDto>>(emptyList())
    val plans = _plans.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _err = MutableStateFlow<String?>(null)
    val err = _err.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _err.value = null
            repo.upcoming()
                .onSuccess { _plans.value = it }
                .onFailure { _err.value = it.message }
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourPlansScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: TourPlansViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(TourPlansViewModel::class) { TourPlansViewModel(app.tourPlanRepo) }
    })
    val plans by vm.plans.collectAsState()
    val loading by vm.loading.collectAsState()
    val err by vm.err.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tour plans") },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            err?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (plans.isEmpty() && !loading) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No upcoming plans. Plans created in the admin panel or via Phase 3.1 mobile flow appear here.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(plans, key = { it.id }) { plan ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(plan.date, style = MaterialTheme.typography.titleMedium)
                                    AssistChip(onClick = {}, label = { Text(plan.status) })
                                }
                                Spacer(Modifier.height(8.dp))
                                plan.entries.forEach { entry ->
                                    Text("• " + (entry.client?.name ?: entry.area ?: "—"))
                                }
                                if (plan.entries.isEmpty()) Text("(no clients)")
                            }
                        }
                    }
                }
            }
        }
    }
}
