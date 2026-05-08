package com.baseras.fieldpharma.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.camera.rememberPhotoCapture
import com.baseras.fieldpharma.data.remote.ExpensePolicyDto
import com.baseras.fieldpharma.data.remote.ExpenseReq
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewExpenseScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val app = FieldPharmaApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var policy by remember { mutableStateOf<ExpensePolicyDto?>(null) }
    LaunchedEffect(Unit) { policy = app.expenseRepo.myPolicy() }

    var type by remember { mutableStateOf("TA") }
    var typeMenu by remember { mutableStateOf(false) }

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("") }
    var fromLoc by remember { mutableStateOf("") }
    var toLoc by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var modeOfTravel by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    var bill by remember { mutableStateOf<File?>(null) }
    val takeBill = rememberPhotoCapture(ctx, subdir = "bills") { f -> bill = f }

    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    // Auto-calc amount for TA when distance changes
    LaunchedEffect(distance, type, policy) {
        if (type == "TA" && amount.isBlank()) {
            val d = distance.toDoubleOrNull()
            val rate = policy?.taRatePerKm
            if (d != null && rate != null) amount = "%.2f".format(d * rate)
        }
        if (type == "DA" && amount.isBlank()) {
            policy?.daFlatRate?.let { amount = "%.2f".format(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New expense") },
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
            ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { typeMenu = it }) {
                OutlinedTextField(
                    value = when (type) { "TA" -> "Travel (TA)"; "DA" -> "Daily (DA)"; else -> "Actual bill" },
                    onValueChange = {}, readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    listOf("TA" to "Travel (TA)", "DA" to "Daily (DA)", "ACTUAL" to "Actual bill")
                        .forEach { (k, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { type = k; typeMenu = false })
                        }
                }
            }

            OutlinedTextField(
                value = date, onValueChange = { date = it },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            when (type) {
                "TA" -> {
                    OutlinedTextField(
                        value = fromLoc, onValueChange = { fromLoc = it },
                        label = { Text("From") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = toLoc, onValueChange = { toLoc = it },
                        label = { Text("To") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = distance, onValueChange = { distance = it; amount = "" },
                            label = { Text("Distance (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = modeOfTravel, onValueChange = { modeOfTravel = it },
                            label = { Text("Mode") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            placeholder = { Text("bus / train / auto") },
                        )
                    }
                    policy?.let {
                        Text(
                            "Rate: ₹${"%.2f".format(it.taRatePerKm)}/km (your grade)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                "DA" -> {
                    policy?.let {
                        Text(
                            "Daily allowance for your grade: ₹${"%.2f".format(it.daFlatRate)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                "ACTUAL" -> {
                    OutlinedTextField(
                        value = category, onValueChange = { category = it },
                        label = { Text("Category (Hotel / Food / Transport)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = takeBill,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (bill != null) "Bill ready (${bill!!.length() / 1024} KB) — retake" else "Take bill photo")
                    }
                }
            }

            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = remarks, onValueChange = { remarks = it },
                label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth(),
            )

            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt == null) { err = "Enter a valid amount"; return@Button }
                    saving = true; err = null
                    scope.launch {
                        runCatching {
                            app.expenseRepo.submit(
                                ExpenseReq(
                                    date = date,
                                    type = type,
                                    amount = amt,
                                    category = category.ifBlank { null },
                                    fromLocation = fromLoc.ifBlank { null },
                                    toLocation = toLoc.ifBlank { null },
                                    distanceKm = distance.toDoubleOrNull(),
                                    modeOfTravel = modeOfTravel.ifBlank { null },
                                    remarks = remarks.ifBlank { null },
                                ),
                                billFile = if (type == "ACTUAL") bill else null,
                            )
                        }.onSuccess { onCreated() }
                            .onFailure { err = it.message; saving = false }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (saving) "Submitting…" else "Submit")
            }
        }
    }
}
