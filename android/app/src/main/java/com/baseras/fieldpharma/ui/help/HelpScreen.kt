package com.baseras.fieldpharma.ui.help

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.components.SectionHeader
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

private data class Faq(val q: String, val a: String)

private val faqs = listOf(
    Faq("Why does the login take ~30 seconds the first time of the day?",
        "The free backend sleeps after 15 min of inactivity. The first request wakes it up; subsequent ones are fast."),
    Faq("Where is my data when I'm offline?",
        "Visits, attendance, expenses, samples, RCPA are saved locally first and sync when the app is online again."),
    Faq("I'm locked out — the app says the subscription is on hold.",
        "Ask your Admin to pay the invoice from the Billing screen. Your account unlocks automatically after."),
    Faq("Can I edit a submitted DCR?",
        "No — once you Check out, the visit is sealed. If you need to correct something, ask your manager to request an edit."),
    Faq("How do I change my password?",
        "Password changes happen through your Admin for now. Self-serve password change is on the roadmap."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Help & support") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Contact") }
            item {
                ContactRow(Icons.Default.Email, "Email support", "support@fieldpharma.app") {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@fieldpharma.app"))
                    runCatching { ctx.startActivity(i) }
                }
            }
            item {
                ContactRow(Icons.Default.Chat, "WhatsApp us", "Business hours only (10am–7pm IST)") {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/919999999999"))
                    runCatching { ctx.startActivity(i) }
                }
            }
            item { SectionHeader("Frequently asked") }
            items(faqs, key = { it.q }) { FaqRow(it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FaqRow(faq: Faq) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(faq.q, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    faq.a,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun HelpPreview() {
    FieldPharmaTheme { HelpScreen(onBack = {}) }
}
