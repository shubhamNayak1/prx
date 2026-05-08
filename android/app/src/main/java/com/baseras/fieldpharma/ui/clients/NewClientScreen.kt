package com.baseras.fieldpharma.ui.clients

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.ClientCreateReq
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewClientScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val app = FieldPharmaApp.instance
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("DOCTOR") }
    var speciality by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var typeMenuOpen by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            OutlinedTextField(
                value = speciality, onValueChange = { speciality = it },
                label = { Text("Speciality (for doctors)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = city, onValueChange = { city = it },
                label = { Text("City") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (name.isBlank()) { err = "Name is required"; return@Button }
                    saving = true
                    err = null
                    scope.launch {
                        // Capture rep's GPS — used as the client's location since
                        // reps usually add clients while standing in the office/clinic.
                        val geo = app.locationProvider.current()
                        runCatching {
                            app.clientRepo.create(ClientCreateReq(
                                name = name.trim(),
                                type = type,
                                speciality = speciality.ifBlank { null },
                                city = city.ifBlank { null },
                                phone = phone.ifBlank { null },
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
                Text(if (saving) "Saving…" else "Save")
            }
        }
    }
}
