package com.baseras.fieldpharma.ui.invite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.CreateUserReq
import com.baseras.fieldpharma.data.remote.UserRow
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Which managers a given role can report to. Mirrors backend/src/lib/roles.ts. */
private fun parentRolesFor(role: String): List<String> = when (role) {
    "SENIOR_MANAGER" -> listOf("ADMIN")
    "REGIONAL_MANAGER" -> listOf("ADMIN", "SENIOR_MANAGER")
    "FIELD_PERSON" -> listOf("ADMIN", "SENIOR_MANAGER", "REGIONAL_MANAGER")
    else -> emptyList()
}

data class InviteUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "FIELD_PERSON",
    val grade: String = "MR1",
    val employeeCode: String = "",
    val managerId: String? = null,
    val tempPassword: String = genPassword(),
    val managers: List<UserRow> = emptyList(),
    val loadingManagers: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val success: UserRow? = null,
)

private fun genPassword(): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('2'..'9')
    return (1..8).map { chars[Random.nextInt(chars.size)] }.joinToString("")
}

class InviteViewModel(private val api: Api) : ViewModel() {
    private val _s = MutableStateFlow(InviteUiState())
    val state = _s.asStateFlow()

    init { loadManagers() }

    private fun loadManagers() {
        viewModelScope.launch {
            runCatching { api.listUsers() }
                .onSuccess { res ->
                    _s.value = _s.value.copy(
                        managers = res.users.filter { it.isActive && it.role != "FIELD_PERSON" },
                        loadingManagers = false,
                    )
                }
                .onFailure { _s.value = _s.value.copy(loadingManagers = false, error = it.message) }
        }
    }

    fun set(update: (InviteUiState) -> InviteUiState) { _s.value = update(_s.value) }
    fun regenPassword() { _s.value = _s.value.copy(tempPassword = genPassword()) }
    fun dismissSuccess() { _s.value = _s.value.copy(success = null, name = "", email = "", phone = "", employeeCode = "", managerId = null, tempPassword = genPassword()) }
    fun dismissError() { _s.value = _s.value.copy(error = null) }

    fun submit() {
        val s = _s.value
        if (s.name.isBlank() || s.email.isBlank() || s.tempPassword.length < 6) {
            _s.value = s.copy(error = "Name, email and a 6+ char password are required.")
            return
        }
        if (s.role != "ADMIN" && s.managerId == null) {
            _s.value = s.copy(error = "Pick a manager.")
            return
        }
        _s.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching {
                api.createUser(CreateUserReq(
                    name = s.name.trim(),
                    email = s.email.trim().lowercase(),
                    password = s.tempPassword,
                    phone = s.phone.ifBlank { null },
                    role = s.role,
                    grade = s.grade.ifBlank { null },
                    employeeCode = s.employeeCode.ifBlank { null },
                    managerId = s.managerId,
                ))
            }.onSuccess {
                _s.value = _s.value.copy(busy = false, success = it.user)
            }.onFailure {
                _s.value = _s.value.copy(busy = false, error = it.message ?: "Create failed")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteUserScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: InviteViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(InviteViewModel::class) { InviteViewModel(app.api) }
    })
    val s by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Invite user") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                FieldBlock("Full name") {
                    OutlinedTextField(
                        value = s.name,
                        onValueChange = { newVal -> vm.set { it.copy(name = newVal) } },
                        placeholder = { Text("e.g. Amit Singh") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FieldBlock("Work email") {
                    OutlinedTextField(
                        value = s.email,
                        onValueChange = { newVal -> vm.set { it.copy(email = newVal) } },
                        placeholder = { Text("amit@acme.in") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FieldBlock("Phone") {
                    OutlinedTextField(
                        value = s.phone,
                        onValueChange = { newVal -> vm.set { it.copy(phone = newVal) } },
                        placeholder = { Text("98765 43210") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FieldBlock("Role") {
                    RoleSelector(
                        current = s.role,
                        onPick = { r -> vm.set { it.copy(role = r, managerId = null) } },
                    )
                }

                if (s.role != "ADMIN") {
                    FieldBlock("Reports to") {
                        if (s.loadingManagers) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InlineSpinner()
                                Spacer(Modifier.width(8.dp))
                                Text("Loading managers…", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            ManagerDropdown(
                                managers = s.managers.filter { it.role in parentRolesFor(s.role) },
                                selectedId = s.managerId,
                                onPick = { id -> vm.set { it.copy(managerId = id) } },
                            )
                        }
                    }
                }

                FieldBlock("Grade (optional)") {
                    OutlinedTextField(
                        value = s.grade,
                        onValueChange = { newVal -> vm.set { it.copy(grade = newVal) } },
                        placeholder = { Text("MR1 / ASM / RSM") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FieldBlock("Employee code (optional)") {
                    OutlinedTextField(
                        value = s.employeeCode,
                        onValueChange = { newVal -> vm.set { it.copy(employeeCode = newVal) } },
                        placeholder = { Text("MR001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FieldBlock("Temporary password (share with user)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = s.tempPassword,
                            onValueChange = { newVal -> vm.set { it.copy(tempPassword = newVal) } },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { vm.regenPassword() }) { Text("Regenerate") }
                    }
                }

                s.error?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::dismissError) { Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { vm.submit() },
                    enabled = !s.busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (s.busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Create user", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(16.dp))
            }

            LoadingOverlay(visible = s.busy, label = "Creating user…")

            s.success?.let { user ->
                SuccessDialog(user = user, password = s.tempPassword, onClose = {
                    vm.dismissSuccess()
                    onBack()
                }, onAddAnother = vm::dismissSuccess)
            }
        }
    }
}

@Composable
private fun FieldBlock(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun RoleSelector(current: String, onPick: (String) -> Unit) {
    val roles = listOf(
        "FIELD_PERSON" to "Field Person",
        "REGIONAL_MANAGER" to "Regional Manager",
        "SENIOR_MANAGER" to "Senior Manager",
        "ADMIN" to "Admin",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        roles.forEach { (key, label) ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (key == current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPick(key) },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = key == current, onClick = { onPick(key) })
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerDropdown(
    managers: List<UserRow>,
    selectedId: String?,
    onPick: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = managers.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${roleLabel(it.role)})" } ?: "— Pick a manager —",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (managers.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No eligible manager yet", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                )
            }
            managers.forEach { m ->
                DropdownMenuItem(
                    text = { Text("${m.name}  ·  ${roleLabel(m.role)}") },
                    onClick = {
                        onPick(m.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "ADMIN" -> "Admin"
    "SENIOR_MANAGER" -> "Senior Manager"
    "REGIONAL_MANAGER" -> "Regional Manager"
    "FIELD_PERSON" -> "Field Person"
    else -> role
}

@Composable
private fun SuccessDialog(user: UserRow, password: String, onClose: () -> Unit, onAddAnother: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("User created") },
        text = {
            Column {
                Text("Share these details with the user — the password only shows once.")
                Spacer(Modifier.height(12.dp))
                KeyValue("Name", user.name)
                KeyValue("Email", user.email)
                KeyValue("Role", roleLabel(user.role))
                KeyValue("Password", password)
            }
        },
        confirmButton = {
            TextButton(onClick = onAddAnother) { Text("Add another") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Done") }
        },
    )
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1100)
@Composable
private fun InvitePreview() {
    FieldPharmaTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldBlock("Full name") { OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth()) }
            FieldBlock("Role") { RoleSelector("FIELD_PERSON") {} }
        }
    }
}
