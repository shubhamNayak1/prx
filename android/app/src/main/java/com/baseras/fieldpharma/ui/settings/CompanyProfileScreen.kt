package com.baseras.fieldpharma.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.CompanyUpdateReq
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import com.baseras.fieldpharma.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompanyUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val name: String = "",
    val gstin: String = "",
    val billingEmail: String = "",
    val billingPhone: String = "",
    val error: String? = null,
)

class CompanyProfileViewModel(private val api: Api) : ViewModel() {
    private val _s = MutableStateFlow(CompanyUiState())
    val state = _s.asStateFlow()

    init { load() }

    fun load() {
        _s.value = _s.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { api.getCompany() }
                .onSuccess { r ->
                    _s.value = CompanyUiState(
                        loading = false,
                        name = r.company.name,
                        gstin = r.company.gstin.orEmpty(),
                        billingEmail = r.company.billingEmail.orEmpty(),
                        billingPhone = r.company.billingPhone.orEmpty(),
                    )
                }
                .onFailure { _s.value = _s.value.copy(loading = false, error = it.message) }
        }
    }

    fun update(copy: CompanyUiState.() -> CompanyUiState) { _s.value = _s.value.copy().copy() }
    fun setName(v: String) { _s.value = _s.value.copy(name = v) }
    fun setGstin(v: String) { _s.value = _s.value.copy(gstin = v.uppercase().take(15)) }
    fun setBillingEmail(v: String) { _s.value = _s.value.copy(billingEmail = v) }
    fun setBillingPhone(v: String) { _s.value = _s.value.copy(billingPhone = v.filter { it.isDigit() }.take(15)) }

    fun save() {
        val s = _s.value
        if (s.name.length < 2) {
            _s.value = s.copy(error = "Company name is required")
            return
        }
        if (s.gstin.isNotBlank() && s.gstin.length != 15) {
            _s.value = s.copy(error = "GSTIN must be exactly 15 characters")
            return
        }
        _s.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                api.updateCompany(CompanyUpdateReq(
                    name = s.name,
                    gstin = s.gstin,
                    billingEmail = s.billingEmail,
                    billingPhone = s.billingPhone,
                ))
            }.onSuccess {
                _s.value = _s.value.copy(saving = false, savedAt = System.currentTimeMillis())
            }.onFailure {
                _s.value = _s.value.copy(saving = false, error = it.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyProfileScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: CompanyProfileViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(CompanyProfileViewModel::class) { CompanyProfileViewModel(app.api) }
    })
    val s by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Company profile") },
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
            if (s.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Identity")
                    OutlinedTextField(
                        value = s.name, onValueChange = vm::setName,
                        label = { Text("Company name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = s.gstin, onValueChange = vm::setGstin,
                        label = { Text("GSTIN (15 characters)") },
                        placeholder = { Text("27AAAAA0000A1Z5") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("Billing contact")
                    OutlinedTextField(
                        value = s.billingEmail, onValueChange = vm::setBillingEmail,
                        label = { Text("Billing email") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = s.billingPhone, onValueChange = vm::setBillingPhone,
                        label = { Text("Billing phone") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    s.error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (s.savedAt != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Saved", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = vm::save, enabled = !s.saving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(if (s.saving) "Saving…" else "Save")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            LoadingOverlay(visible = s.saving, label = "Saving…")
        }
    }
}
