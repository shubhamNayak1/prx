package com.baseras.fieldpharma.ui.edetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.local.DeckEntity
import com.baseras.fieldpharma.data.repo.EdetailRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DecksViewModel(private val repo: EdetailRepository) : ViewModel() {
    val decks = repo.observeDecks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val refreshing = androidx.compose.runtime.mutableStateOf(false)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            repo.refresh()
            refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(onBack: () -> Unit, onOpen: (DeckEntity) -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: DecksViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(DecksViewModel::class) { DecksViewModel(app.edetailRepo) }
    })
    val decks by vm.decks.collectAsState()
    val refreshing by vm.refreshing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-detailing") },
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
            if (refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (decks.isEmpty() && !refreshing) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No decks yet. Once your manager publishes a presentation, it'll appear here and download for offline use.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(decks, key = { it.id }) { deck ->
                        ListItem(
                            headlineContent = { Text(deck.name) },
                            supportingContent = { Text(deck.product ?: "—") },
                            modifier = Modifier.clickable { onOpen(deck) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
