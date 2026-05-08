package com.baseras.fieldpharma.ui.rcpa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.local.ClientEntity
import com.baseras.fieldpharma.data.remote.RcpaReq
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRcpaScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val app = FieldPharmaApp.instance
    val scope = rememberCoroutineScope()

    var chemists by remember { mutableStateOf<List<ClientEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        app.clientRepo.observeAll().collectLatest { all ->
            chemists = all.filter { it.type == "CHEMIST" }
        }
    }

    var clientId by remember { mutableStateOf("") }
    var clientMenu by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var ourBrand by remember { mutableStateOf("") }
    var ourQty by remember { mutableStateOf("") }
    var compBrand by remember { mutableStateOf("") }
    var compQty by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New RCPA") },
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
            ExposedDropdownMenuBox(expanded = clientMenu, onExpandedChange = { clientMenu = it }) {
                OutlinedTextField(
                    value = chemists.firstOrNull { it.id == clientId }?.name ?: "Pick chemist",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Chemist") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = clientMenu, onDismissRequest = { clientMenu = false }) {
                    if (chemists.isEmpty()) {
                        DropdownMenuItem(text = { Text("(No chemists in cache — sync clients first)") }, onClick = {})
                    }
                    chemists.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.name} • ${c.city ?: "—"}") },
                            onClick = { clientId = c.id; clientMenu = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = date, onValueChange = { date = it },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("Our brand", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ourBrand, onValueChange = { ourBrand = it },
                    label = { Text("Brand name") },
                    singleLine = true, modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = ourQty, onValueChange = { ourQty = it.filter { c -> c.isDigit() } },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
            }

            Text("Competitor brand", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = compBrand, onValueChange = { compBrand = it },
                    label = { Text("Brand name") },
                    singleLine = true, modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = compQty, onValueChange = { compQty = it.filter { c -> c.isDigit() } },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = remarks, onValueChange = { remarks = it },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth(),
            )

            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val oq = ourQty.toIntOrNull(); val cq = compQty.toIntOrNull()
                    if (clientId.isBlank()) { err = "Pick a chemist"; return@Button }
                    if (ourBrand.isBlank() || oq == null || compBrand.isBlank() || cq == null) {
                        err = "Fill brand name + quantity for both"; return@Button
                    }
                    saving = true; err = null
                    scope.launch {
                        val geo = app.locationProvider.current()
                        runCatching {
                            app.rcpaRepo.submit(RcpaReq(
                                clientId = clientId, date = date,
                                ourBrand = ourBrand.trim(), ourQuantity = oq,
                                competitorBrand = compBrand.trim(), competitorQuantity = cq,
                                remarks = remarks.ifBlank { null },
                                actionLat = geo?.lat,
                                actionLng = geo?.lng,
                            ))
                        }.onSuccess { onCreated() }
                            .onFailure { err = it.message; saving = false }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (saving) "Saving…" else "Save") }
        }
    }
}
