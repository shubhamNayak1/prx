package com.baseras.fieldpharma.ui.attendance

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.camera.rememberSelfieCapture
import com.baseras.fieldpharma.data.local.AttendanceEntity
import com.baseras.fieldpharma.data.repo.AttendanceRepository
import com.baseras.fieldpharma.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AttendanceViewModel(
    private val repo: AttendanceRepository,
    private val location: LocationProvider,
) : ViewModel() {
    val today = repo.observeToday().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selfie = MutableStateFlow<File?>(null)
    val selfie = _selfie.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun setSelfie(f: File?) { _selfie.value = f }

    fun punchIn() {
        if (_busy.value) return
        _busy.value = true
        _msg.value = null
        viewModelScope.launch {
            val geo = location.current()
            repo.punchIn(geo?.lat, geo?.lng, _selfie.value)
            _busy.value = false
            _selfie.value = null
            _msg.value = "Punched in" + if (geo == null) " (no location yet)" else ""
        }
    }

    fun punchOut() {
        if (_busy.value) return
        _busy.value = true
        _msg.value = null
        viewModelScope.launch {
            val geo = location.current()
            repo.punchOut(geo?.lat, geo?.lng)
            _busy.value = false
            _msg.value = "Punched out"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val ctx = LocalContext.current
    val vm: AttendanceViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(AttendanceViewModel::class) { AttendanceViewModel(app.attendanceRepo, app.locationProvider) }
    })
    val today by vm.today.collectAsState()
    val msg by vm.msg.collectAsState()
    val busy by vm.busy.collectAsState()
    val selfie by vm.selfie.collectAsState()

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {}

    val takeSelfie = rememberSelfieCapture(ctx) { file -> vm.setSelfie(file) }

    LaunchedEffect(Unit) {
        if (!app.locationProvider.hasPermission()) {
            locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
    }

    val punchedIn = today?.punchInAt != null
    val punchedOut = today?.punchOutAt != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(today)

            if (!punchedIn) {
                OutlinedButton(
                    onClick = takeSelfie,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selfie != null) "Selfie ready (${(selfie!!.length() / 1024)} KB) — retake"
                        else "Take attendance selfie (optional)"
                    )
                }
            }

            Button(
                onClick = { vm.punchIn() },
                enabled = !busy && !punchedIn,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (punchedIn) "Already punched in" else "Punch In")
            }
            OutlinedButton(
                onClick = { vm.punchOut() },
                enabled = !busy && punchedIn && !punchedOut,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (punchedOut) "Already punched out" else "Punch Out")
            }

            msg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusCard(today: AttendanceEntity?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today", style = MaterialTheme.typography.labelMedium)
            Text(today?.date ?: "—", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Punch in", style = MaterialTheme.typography.labelSmall)
                    Text(today?.punchInAt?.take(19)?.replace("T", " ") ?: "—")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Punch out", style = MaterialTheme.typography.labelSmall)
                    Text(today?.punchOutAt?.take(19)?.replace("T", " ") ?: "—")
                }
            }
            today?.let {
                if (it.syncedAt == null && (it.punchInAt != null || it.punchOutAt != null)) {
                    AssistChip(onClick = {}, label = { Text("Pending sync") })
                }
            }
        }
    }
}
