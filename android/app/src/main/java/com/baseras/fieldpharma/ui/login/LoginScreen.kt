package com.baseras.fieldpharma.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.R
import com.baseras.fieldpharma.data.repo.AuthRepository
import com.baseras.fieldpharma.ui.components.InlineSpinner
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* ──────────────────────────── State ──────────────────────────── */

sealed interface Step {
    data object Phone : Step
    data class Otp(val phone: String, val channel: String, val expiresAt: String, val devCode: String?) : Step
    data class Pin(val phone: String, val isCreate: Boolean) : Step             // isCreate=false → login w/ PIN; true → create (new user or reset)
    data class CreatePin(val phone: String, val setupToken: String, val forNewUser: Boolean) : Step
    data class Company(val phone: String, val setupToken: String, val pin: String) : Step
}

data class LoginUiState(
    val step: Step = Step.Phone,
    val busy: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val resetMode: Boolean = false,
)

class LoginViewModel(
    private val auth: AuthRepository,
    private val billing: com.baseras.fieldpharma.data.repo.BillingRepository,
) : ViewModel() {
    private val _s = MutableStateFlow(LoginUiState())
    val state = _s.asStateFlow()

    fun resetError() { _s.value = _s.value.copy(error = null) }
    fun goTo(step: Step) { _s.value = LoginUiState(step = step) }

    /* ───── Phone step ───── */
    fun sendOtp(phone: String, purpose: String = "LOGIN") {
        if (!phone.matches(Regex("^\\d{10}$"))) {
            _s.value = _s.value.copy(error = "Enter 10 digits (don't include +91 or 0)")
            return
        }
        _s.value = _s.value.copy(busy = true, error = null, resetMode = purpose == "RESET_PIN")
        viewModelScope.launch {
            auth.requestOtp(phone, purpose)
                .onSuccess { r ->
                    _s.value = _s.value.copy(
                        busy = false,
                        step = Step.Otp(phone, r.channel, r.expiresAt, r.devCode),
                    )
                }
                .onFailure {
                    _s.value = _s.value.copy(busy = false, error = it.message ?: "Could not send OTP")
                }
        }
    }

    /** Shortcut: skip OTP for returning-user PIN login. */
    fun goToPinEntry(phone: String) {
        if (!phone.matches(Regex("^\\d{10}$"))) {
            _s.value = _s.value.copy(error = "Enter 10 digits (don't include +91 or 0)")
            return
        }
        _s.value = LoginUiState(step = Step.Pin(phone, isCreate = false))
    }

    /* ───── OTP verify ───── */
    fun verifyOtp(phone: String, otp: String) {
        if (!otp.matches(Regex("^\\d{6}$"))) {
            _s.value = _s.value.copy(error = "Enter the 6-digit code we sent")
            return
        }
        val reset = _s.value.resetMode
        _s.value = _s.value.copy(busy = true, error = null)
        viewModelScope.launch {
            auth.verifyOtp(phone, otp, purpose = if (reset) "RESET_PIN" else "LOGIN")
                .onSuccess { r ->
                    _s.value = _s.value.copy(busy = false, step = when {
                        !r.exists -> Step.CreatePin(phone, r.setupToken, forNewUser = true)
                        !r.hasPin -> Step.CreatePin(phone, r.setupToken, forNewUser = false)
                        reset     -> Step.CreatePin(phone, r.setupToken, forNewUser = false)
                        else      -> Step.Pin(phone, isCreate = false)
                    })
                }
                .onFailure { _s.value = _s.value.copy(busy = false, error = friendly(it.message)) }
        }
    }

    /* ───── Existing user — PIN login ───── */
    fun loginWithPin(phone: String, pin: String, onDone: () -> Unit) {
        if (!pin.matches(Regex("^\\d{4}$"))) {
            _s.value = _s.value.copy(error = "PIN must be 4 digits")
            return
        }
        _s.value = _s.value.copy(busy = true, error = null)
        viewModelScope.launch {
            auth.pinLogin(phone, pin)
                .onSuccess {
                    runCatching { billing.refresh() }
                    _s.value = _s.value.copy(busy = false, success = true)
                    onDone()
                }
                .onFailure { t ->
                    val msg = parseErrorMessage(t)
                    val friendly = when {
                        msg.contains("PIN_NOT_SET", true) ->
                            "No PIN set for this number. Tap 'First time? Send WhatsApp code' to set one."
                        msg.contains("Invalid credentials", true) ->
                            "Phone or PIN is wrong. If this is a new number, use 'Send WhatsApp code' instead."
                        else -> msg
                    }
                    _s.value = _s.value.copy(busy = false, error = friendly)
                }
        }
    }

    /* ───── Create PIN (both existing-no-PIN and new-user flows) ───── */
    fun savePin(step: Step.CreatePin, pin: String, confirm: String, onDone: () -> Unit) {
        if (pin != confirm) {
            _s.value = _s.value.copy(error = "PINs don't match")
            return
        }
        if (!pin.matches(Regex("^\\d{4}$"))) {
            _s.value = _s.value.copy(error = "PIN must be 4 digits")
            return
        }
        if (step.forNewUser) {
            // Advance to Company step; backend write happens there via signupComplete.
            _s.value = LoginUiState(step = Step.Company(step.phone, step.setupToken, pin))
            return
        }
        _s.value = _s.value.copy(busy = true, error = null)
        viewModelScope.launch {
            auth.setPin(step.setupToken, pin, confirm)
                .onSuccess {
                    runCatching { billing.refresh() }
                    _s.value = _s.value.copy(busy = false, success = true)
                    onDone()
                }
                .onFailure { _s.value = _s.value.copy(busy = false, error = it.message ?: "Could not set PIN") }
        }
    }

    /* ───── New-user company creation ───── */
    fun completeSignup(step: Step.Company, name: String, email: String, companyName: String, gstin: String, onDone: () -> Unit) {
        _s.value = _s.value.copy(busy = true, error = null)
        viewModelScope.launch {
            auth.signupComplete(
                setupToken = step.setupToken,
                companyName = companyName,
                gstin = gstin.ifBlank { null },
                adminName = name,
                adminEmail = email,
                pin = step.pin,
            ).onSuccess {
                runCatching { billing.refresh() }
                _s.value = _s.value.copy(busy = false, success = true)
                onDone()
            }.onFailure {
                _s.value = _s.value.copy(busy = false, error = it.message ?: "Signup failed")
            }
        }
    }
}

private fun friendly(msg: String?): String = when {
    msg == null -> "Something went wrong"
    msg.contains("OTP_INVALID", true) || msg.contains("INCORRECT_CODE", true) -> "That code is wrong"
    msg.contains("EXPIRED_OR_UNKNOWN", true) -> "Code expired — tap Resend"
    msg.contains("TOO_MANY", true) -> "Too many tries — tap Resend"
    msg.contains("RATE_LIMITED", true) -> "Too many requests. Try again later."
    else -> msg
}

/** Pulls the backend's error.message / error body out of a Retrofit HttpException. */
internal fun parseErrorMessage(t: Throwable): String {
    val http = t as? retrofit2.HttpException ?: return t.message ?: "Network error"
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull() ?: return "HTTP ${http.code()}"
    val match = Regex("""\"(?:error|message)\":\s*\"([^\"]+)\"""").find(body)
    return match?.groupValues?.get(1) ?: "HTTP ${http.code()}: $body"
}

/* ──────────────────────────── Screen ──────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val app = FieldPharmaApp.instance
    val vm: LoginViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(LoginViewModel::class) { LoginViewModel(app.authRepo, app.billingRepo) }
    })
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Baserastech Field Pharma",
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(16.dp))

                when (val step = state.step) {
                    Step.Phone -> PhoneStep(state, vm)
                    is Step.Otp -> OtpStep(step, state, vm, onSignedIn)
                    is Step.Pin -> PinStep(step, state, vm, onSignedIn)
                    is Step.CreatePin -> CreatePinStep(step, state, vm, onSignedIn)
                    is Step.Company -> CompanyStep(step, state, vm, onSignedIn)
                }

                Spacer(Modifier.height(24.dp))
            }
            LoadingOverlay(visible = state.busy, label = loadingLabel(state.step))
        }
    }
}

private fun loadingLabel(step: Step): String = when (step) {
    Step.Phone -> "Sending code…"
    is Step.Otp -> "Verifying…"
    is Step.Pin -> "Signing in…"
    is Step.CreatePin -> "Saving PIN…"
    is Step.Company -> "Creating company…"
}

/* ─────── Phone step ─────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneStep(state: LoginUiState, vm: LoginViewModel) {
    var phone by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Welcome", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Enter your WhatsApp number to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(15) },
            label = { Text("WhatsApp number") },
            placeholder = { Text("98765 43210") },
            singleLine = true,
            leadingIcon = { Text("+91", style = MaterialTheme.typography.bodyLarge) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { Spacer(Modifier.height(8.dp)); ErrorText(it) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.goToPinEntry(phone) },
            enabled = !state.busy && phone.length >= 7,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Text("I have a PIN — sign in", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { vm.sendOtp(phone) },
            enabled = !state.busy && phone.length >= 7,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            if (state.busy) InlineSpinner()
            else Text("First time? Send WhatsApp code", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/* ─────── OTP step ─────── */

@Composable
private fun OtpStep(step: Step.Otp, state: LoginUiState, vm: LoginViewModel, onSignedIn: () -> Unit) {
    var otp by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Verify number", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Code sent via ${channelLabel(step.channel)} to +91 ${step.phone}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        step.devCode?.let {
            Spacer(Modifier.height(8.dp))
            DevHint("Dev-mode (no SMS provider): code is $it")
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Spacer(Modifier.height(8.dp)); ErrorText(it) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.verifyOtp(step.phone, otp) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            if (state.busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
            else Text("Verify", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = { vm.goTo(Step.Phone) }) { Text("Change number") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.sendOtp(step.phone) }) { Text("Resend code") }
        }
    }
}

/* ─────── PIN login step (returning user) ─────── */

@Composable
private fun PinStep(step: Step.Pin, state: LoginUiState, vm: LoginViewModel, onSignedIn: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter your PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "+91 ${step.phone}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("4-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Spacer(Modifier.height(8.dp)); ErrorText(it) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.loginWithPin(step.phone, pin, onSignedIn) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            if (state.busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
            else Text("Sign in", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.sendOtp(step.phone, "RESET_PIN") }) {
            Text("Forgot PIN? Reset via WhatsApp")
        }
    }
}

/* ─────── Create PIN step ─────── */

@Composable
private fun CreatePinStep(step: Step.CreatePin, state: LoginUiState, vm: LoginViewModel, onSignedIn: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val title = if (step.forNewUser) "Create your PIN" else "Set a 4-digit PIN"
    val sub = if (step.forNewUser)
        "You're new here — set a PIN, then we'll create your company."
    else
        "Use this PIN to sign in next time. You can reset it any time with WhatsApp."

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("PIN (4 digits)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("Re-enter PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Spacer(Modifier.height(8.dp)); ErrorText(it) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.savePin(step, pin, confirm, onSignedIn) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            if (state.busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (step.forNewUser) "Continue" else "Save PIN & sign in", style = MaterialTheme.typography.labelLarge)
        }

        // Biometric nudge after a successful create — not wired to actual BiometricPrompt yet.
        if (state.success) {
            Spacer(Modifier.height(12.dp))
            BiometricNudge()
        }
    }
}

/* ─────── Company (new user) step ─────── */

@Composable
private fun CompanyStep(step: Step.Company, state: LoginUiState, vm: LoginViewModel, onSignedIn: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Create your company", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "You'll be the Admin. 7-day free trial — no card needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = companyName, onValueChange = { companyName = it },
            label = { Text("Company name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = gstin, onValueChange = { gstin = it.uppercase().take(15) },
            label = { Text("GSTIN (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Your name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Work email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Spacer(Modifier.height(8.dp)); ErrorText(it) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.completeSignup(step, name, email, companyName, gstin, onSignedIn) },
            enabled = !state.busy && companyName.length >= 2 && name.isNotBlank() && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            if (state.busy) InlineSpinner(color = MaterialTheme.colorScheme.onPrimary)
            else Text("Create & sign in", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/* ─────── Shared bits ─────── */

@Composable
private fun ErrorText(msg: String) {
    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DevHint(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BiometricNudge() {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Enable biometric login",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Coming soon — unlock with your fingerprint instead of typing the PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = { dismissed = true }) {
                Text("Not now", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private fun channelLabel(c: String): String = when (c) {
    "whatsapp" -> "WhatsApp"
    "sms" -> "SMS"
    else -> "the server console (dev)"
}
