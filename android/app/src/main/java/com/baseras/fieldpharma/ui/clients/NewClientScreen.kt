package com.baseras.fieldpharma.ui.clients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.ClientCreateReq
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import com.baseras.fieldpharma.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewClientScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val app = FieldPharmaApp.instance
    val scope = rememberCoroutineScope()

    // Core
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("DOCTOR") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var speciality by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // Address (now a proper multi-field block)
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New client") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionHeader("Basic details")
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
                    OutlinedTextField(
                        value = type, onValueChange = {}, readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                        listOf("DOCTOR", "CHEMIST", "STOCKIST", "HOSPITAL").forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = { type = t; typeMenuOpen = false },
                            )
                        }
                    }
                }
                if (type == "DOCTOR") {
                    OutlinedTextField(
                        value = speciality, onValueChange = { speciality = it },
                        label = { Text("Speciality") }, placeholder = { Text("Cardiology, Pediatrics…") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
                SectionHeader("Contact")
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
                    label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email (optional)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                SectionHeader("Address")
                OutlinedTextField(
                    value = addressLine, onValueChange = { addressLine = it },
                    label = { Text("Street / clinic address") },
                    placeholder = { Text("Shop 4, Baker Rd, near Metro Station") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = city, onValueChange = { city = it },
                        label = { Text("City") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state, onValueChange = { state = it },
                        label = { Text("State") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = pincode,
                    onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Pincode") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The rep's current GPS is captured automatically on save.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                err?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        when {
                            name.isBlank() -> { err = "Name is required"; return@Button }
                            pincode.isNotBlank() && pincode.length != 6 -> { err = "Pincode must be 6 digits"; return@Button }
                        }
                        saving = true; err = null
                        scope.launch {
                            val geo = app.locationProvider.current()
                            val composedAddress = buildString {
                                if (addressLine.isNotBlank()) append(addressLine.trim())
                                if (state.isNotBlank()) {
                                    if (isNotEmpty()) append(", ")
                                    append(state.trim())
                                }
                            }.ifBlank { null }
                            runCatching {
                                app.clientRepo.create(ClientCreateReq(
                                    name = name.trim(),
                                    type = type,
                                    speciality = speciality.ifBlank { null },
                                    address = composedAddress,
                                    city = city.ifBlank { null },
                                    pincode = pincode.ifBlank { null },
                                    phone = phone.ifBlank { null },
                                    email = email.ifBlank { null },
                                    latitude = geo?.lat,
                                    longitude = geo?.lng,
                                ))
                            }.onSuccess { onCreated() }
                                .onFailure { err = it.message; saving = false }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (saving) "Saving…" else "Save client")
                }
                Spacer(Modifier.height(16.dp))
            }
            LoadingOverlay(visible = saving, label = "Saving client…")
        }
    }
}
