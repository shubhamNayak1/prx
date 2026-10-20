package com.baseras.fieldpharma.ui.team

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
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.TeamRollup
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamUiState(
    val loading: Boolean = true,
    val team: List<TeamRollup> = emptyList(),
    val error: String? = null,
)

class TeamViewModel(private val api: Api) : ViewModel() {
    private val _state = MutableStateFlow(TeamUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { api.roleSummary() }
                .onSuccess { _state.value = _state.value.copy(loading = false, team = it.team) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: TeamViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(TeamViewModel::class) { TeamViewModel(app.api) }
    })
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("My team", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text(
                    "Couldn't load team: ${state.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                state.team.isEmpty() -> EmptyTeam()
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        item {
                            SectionHeader("${state.team.size} member${if (state.team.size == 1) "" else "s"}")
                        }
                        items(state.team, key = { it.id }) { m ->
                            TeamMemberItem(
                                member = TeamMember(
                                    name = m.name,
                                    roleLabel = roleLabel(m),
                                    status = when (m.status) {
                                        "ActiveToday" -> TeamMemberStatus.ActiveToday
                                        "NotPunched" -> TeamMemberStatus.NotPunched
                                        "Flagged" -> TeamMemberStatus.Flagged
                                        else -> TeamMemberStatus.OffField
                                    },
                                    progressLine = progressLine(m),
                                ),
                                onClick = { /* TODO: drill into user detail */ },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(m: TeamRollup): String = when (m.role) {
    "FIELD_PERSON"     -> "Field · ${m.grade ?: "—"}"
    "REGIONAL_MANAGER" -> "Regional · ${m.totalReps} rep${if (m.totalReps == 1) "" else "s"}"
    "SENIOR_MANAGER"   -> "Senior · ${m.totalReps} rep${if (m.totalReps == 1) "" else "s"}"
    else -> m.role
}

private fun progressLine(m: TeamRollup): String = when (m.role) {
    "FIELD_PERSON" -> "${m.callsToday} / ${m.callsPlanned} calls"
    else -> "${m.activeToday}/${m.totalReps} active · ${m.attainmentPct}%"
}

@Composable
private fun EmptyTeam() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "No team members yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ask your admin to assign direct reports to you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
