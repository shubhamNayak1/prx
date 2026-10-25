package com.baseras.fieldpharma.ui.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.UserRow
import com.baseras.fieldpharma.ui.components.LoadingOverlay
import com.baseras.fieldpharma.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class DcrRange(val label: String) {
    Today("Today"),
    ThisWeek("This week"),
    ThisMonth("This month"),
    LastMonth("Last month"),
    Custom("Custom"),
}

data class DcrDownloadUi(
    val loading: Boolean = true,
    val me: UserRow? = null,
    val reports: List<UserRow> = emptyList(),
    val selectedUserId: String? = null,
    val range: DcrRange = DcrRange.ThisMonth,
    val fromIso: String = "",
    val toIso: String = "",
    val busy: Boolean = false,
    val lastFilePath: String? = null,
    val lastFileName: String? = null,
    val error: String? = null,
    val info: String? = null,
)

class DcrDownloadViewModel(private val api: Api, private val myId: String?, private val myRole: String) : ViewModel() {
    private val _s = MutableStateFlow(DcrDownloadUi())
    val state = _s.asStateFlow()

    init {
        setRange(DcrRange.ThisMonth)
        load()
    }

    private fun load() {
        _s.value = _s.value.copy(loading = true)
        viewModelScope.launch {
            runCatching { api.listUsers().users }
                .onSuccess { users ->
                    val me = users.firstOrNull { it.id == myId }
                    val reports = when (myRole) {
                        "FIELD_PERSON" -> listOfNotNull(me)
                        "ADMIN" -> users.filter { it.isActive }
                        else -> {
                            // Managers see their descendants.
                            val byMgr = users.groupBy { it.managerId }
                            val out = mutableListOf<UserRow>()
                            val stack = ArrayDeque(byMgr[myId].orEmpty())
                            while (stack.isNotEmpty()) {
                                val u = stack.removeFirst()
                                if (u.isActive) out += u
                                stack.addAll(byMgr[u.id].orEmpty())
                            }
                            out
                        }
                    }
                    _s.value = _s.value.copy(
                        loading = false,
                        me = me,
                        reports = reports,
                        selectedUserId = _s.value.selectedUserId ?: me?.id ?: reports.firstOrNull()?.id,
                    )
                }
                .onFailure { _s.value = _s.value.copy(loading = false, error = it.message) }
        }
    }

    fun selectUser(id: String) { _s.value = _s.value.copy(selectedUserId = id) }

    fun setRange(r: DcrRange) {
        val today = LocalDate.now()
        val (f, t) = when (r) {
            DcrRange.Today -> today to today
            DcrRange.ThisWeek -> today.minusDays(today.dayOfWeek.value.toLong() - 1) to today
            DcrRange.ThisMonth -> today.withDayOfMonth(1) to today
            DcrRange.LastMonth -> {
                val lm = YearMonth.from(today).minusMonths(1)
                lm.atDay(1) to lm.atEndOfMonth()
            }
            DcrRange.Custom -> _s.value.let {
                (if (it.fromIso.isNotEmpty()) LocalDate.parse(it.fromIso) else today) to
                (if (it.toIso.isNotEmpty()) LocalDate.parse(it.toIso) else today)
            }
        }
        _s.value = _s.value.copy(range = r, fromIso = f.toString(), toIso = t.toString())
    }

    fun setCustom(from: String?, to: String?) {
        _s.value = _s.value.copy(
            range = DcrRange.Custom,
            fromIso = from ?: _s.value.fromIso,
            toIso = to ?: _s.value.toIso,
        )
    }

    fun download(ctx: Context, onReady: (File, String) -> Unit) {
        val s = _s.value
        val uid = s.selectedUserId ?: return
        _s.value = s.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            runCatching {
                val resp = api.downloadDcr(s.fromIso, s.toIso, uid)
                if (!resp.isSuccessful) error("Server returned ${resp.code()}")
                val body = resp.body() ?: error("Empty response")
                val name = s.reports.firstOrNull { it.id == uid }?.name?.replace(Regex("\\s+"), "_") ?: "DCR"
                val file = File(ctx.cacheDir, "${name}_DCR_${s.fromIso}_to_${s.toIso}.xlsx")
                file.outputStream().use { os -> body.byteStream().use { it.copyTo(os) } }
                file
            }.onSuccess { f ->
                _s.value = _s.value.copy(busy = false, lastFilePath = f.absolutePath, lastFileName = f.name, info = "Downloaded")
                onReady(f, f.name)
            }.onFailure {
                _s.value = _s.value.copy(busy = false, error = it.message ?: "Download failed")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DcrDownloadScreen(onBack: () -> Unit) {
    val app = FieldPharmaApp.instance
    val ctx = LocalContext.current
    val me = app.authRepo.current

    val vm: DcrDownloadViewModel = viewModel(factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(DcrDownloadViewModel::class) {
            DcrDownloadViewModel(app.api, me?.id, me?.role ?: "FIELD_PERSON")
        }
    })
    val s by vm.state.collectAsState()

    val isManager = (me?.role ?: "FIELD_PERSON") != "FIELD_PERSON"
    var showUserPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Daily Field Report") },
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
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Generate the DCR workbook (one sheet per working day) and share it with your manager via WhatsApp.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isManager) {
                    SectionHeader("Field person")
                    val selected = s.reports.firstOrNull { it.id == s.selectedUserId }
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showUserPicker = true },
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(selected?.name ?: "Pick a person", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    selected?.let { "${it.role.replace('_', ' ')}  ·  ${it.employeeCode ?: "—"}" } ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.Description, null)
                        }
                    }
                }

                SectionHeader("Date range")
                RangeChipRow(current = s.range, onPick = vm::setRange)
                Text(
                    "${s.fromIso}  →  ${s.toIso}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.range == DcrRange.Custom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = s.fromIso, onValueChange = { vm.setCustom(from = it, to = null) },
                            label = { Text("From (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = s.toIso, onValueChange = { vm.setCustom(from = null, to = it) },
                            label = { Text("To (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                FilledTonalButton(
                    enabled = !s.busy && s.selectedUserId != null,
                    onClick = {
                        vm.download(ctx) { file, _ ->
                            openXlsx(ctx, file)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Download & open")
                }
                Button(
                    enabled = !s.busy && s.selectedUserId != null,
                    onClick = {
                        vm.download(ctx) { file, _ -> shareXlsxToWhatsApp(ctx, file) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Download & share on WhatsApp")
                }

                s.lastFileName?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Last file: $it (${s.lastFilePath ?: ""})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                s.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            LoadingOverlay(visible = s.busy, label = "Preparing your DCR…")
        }
    }

    if (showUserPicker) {
        AlertDialog(
            onDismissRequest = { showUserPicker = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showUserPicker = false }) { Text("Close") } },
            title = { Text("Pick a field person") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(s.reports, key = { it.id }) { u ->
                        ListItem(
                            headlineContent = { Text(u.name) },
                            supportingContent = { Text("${u.role.replace('_', ' ')} · ${u.employeeCode ?: "—"}") },
                            trailingContent = {
                                if (u.id == s.selectedUserId) Icon(Icons.Default.Description, null)
                            },
                            modifier = Modifier.clickable {
                                vm.selectUser(u.id)
                                showUserPicker = false
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun RangeChipRow(current: DcrRange, onPick: (DcrRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DcrRange.values().forEach { r ->
            FilterChip(
                selected = r == current,
                onClick = { onPick(r) },
                label = { Text(r.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

private fun fileProviderUri(ctx: Context, file: File): Uri =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

private fun openXlsx(ctx: Context, file: File) {
    val uri = fileProviderUri(ctx, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, XLSX_MIME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(Intent.createChooser(intent, "Open DCR")) }
}

private fun shareXlsxToWhatsApp(ctx: Context, file: File) {
    val uri = fileProviderUri(ctx, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = XLSX_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Prefer WhatsApp if installed, but let the chooser handle the fallback.
    val chooser = Intent.createChooser(send, "Share DCR to…").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(chooser) }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
