package com.baseras.fieldpharma.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.UserRow
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* ─────── State ─────── */

data class TeamUiState(
    val loading: Boolean = true,
    val users: List<UserRow> = emptyList(),
    val myUserId: String? = null,
    val myRole: String = "FIELD_PERSON",
    val error: String? = null,
)

class TeamViewModel(private val api: Api) : ViewModel() {
    private val _state = MutableStateFlow(TeamUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { api.listUsers() }
                .onSuccess { _state.value = _state.value.copy(loading = false, users = it.users) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun setMe(id: String?, role: String) {
        _state.value = _state.value.copy(myUserId = id, myRole = role)
    }
}

/* ─────── Screen ─────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: TeamViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(TeamViewModel::class) { TeamViewModel(app.api) }
    })
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        val me = app.authRepo.current
        vm.setMe(me?.id, me?.role ?: "FIELD_PERSON")
    }

    // Scope visible users based on role:
    //   Admin → whole active company
    //   Managers → descendants in the tree
    //   Field → just themselves (edge case)
    val visibleTree: List<TreeNode> = remember(state.users, state.myUserId, state.myRole) {
        if (state.users.isEmpty()) emptyList()
        else runCatching { buildVisibleTree(state.users, state.myUserId, state.myRole) }
            .getOrElse { emptyList() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val suffix = if (visibleTree.isEmpty()) "" else
                        " · ${visibleTree.size} ${if (visibleTree.size == 1) "member" else "members"}"
                    Text("My team$suffix", style = MaterialTheme.typography.titleLarge)
                },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text(
                    "Couldn't load team: ${state.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                visibleTree.isEmpty() -> EmptyTeam()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleTree, key = { it.row.id }) { node -> TeamNodeRow(node) }
                }
            }
        }
    }
}

/* ─────── Node builder ─────── */

private data class TreeNode(
    val row: UserRow,
    val depth: Int,
    val directReports: Int,
)

private fun buildVisibleTree(all: List<UserRow>, myId: String?, myRole: String): List<TreeNode> {
    val active = all.filter { it.isActive }
    if (active.isEmpty()) return emptyList()
    val byId = active.associateBy { it.id }
    val byParent = active.groupBy { it.managerId }

    val startIds: List<String> = when (myRole) {
        "ADMIN" -> active.filter { it.managerId == null || it.managerId == myId }
            .map { it.id }
            .distinct()
        else -> if (myId == null) emptyList() else byParent[myId]?.map { it.id }.orEmpty()
    }

    val out = mutableListOf<TreeNode>()
    val seen = mutableSetOf<String>()
    fun walk(id: String, depth: Int) {
        if (!seen.add(id)) return
        val row = byId[id] ?: return
        val children = byParent[id].orEmpty()
        out += TreeNode(row = row, depth = depth, directReports = children.size)
        children.sortedBy { it.name }.forEach { walk(it.id, depth + 1) }
    }
    startIds.sortedBy { byId[it]?.name ?: "" }.forEach { walk(it, 0) }
    return out
}

/* ─────── Row ─────── */

@Composable
private fun TeamNodeRow(node: TreeNode) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .padding(start = (12 + node.depth * 20).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(avatarTint(node.row.role)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    node.row.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(node.row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    buildString {
                        append(roleLabel(node.row.role))
                        node.row.grade?.let { append(" · $it") }
                        if (node.directReports > 0) append(" · ${node.directReports} direct")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatusPill(roleLabel(node.row.role), rolePillTone(node.row.role))
        }
    }
}

private fun avatarTint(role: String): Color = when (role) {
    "ADMIN" -> Color(0xFF7C3AED)             // violet
    "SENIOR_MANAGER" -> Color(0xFF2563EB)    // blue
    "REGIONAL_MANAGER" -> Color(0xFF0D9488)  // teal
    else -> Color(0xFF64748B)                // slate for field persons
}

private fun rolePillTone(role: String): PillTone = when (role) {
    "ADMIN" -> PillTone.Accent
    "SENIOR_MANAGER", "REGIONAL_MANAGER" -> PillTone.Primary
    else -> PillTone.Neutral
}

private fun roleLabel(role: String): String = when (role) {
    "ADMIN" -> "Admin"
    "SENIOR_MANAGER" -> "Senior Mgr"
    "REGIONAL_MANAGER" -> "Regional Mgr"
    "FIELD_PERSON" -> "Field"
    else -> role
}

@Composable
private fun EmptyTeam() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No team members yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Invite users from the home screen to populate your hierarchy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
