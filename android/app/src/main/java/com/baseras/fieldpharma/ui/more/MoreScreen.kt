package com.baseras.fieldpharma.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.ui.components.*
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

enum class MoreVariant { FieldPerson, Manager, Admin }

data class MoreItem(
    val icon: ImageVector,
    val label: String,
    val subtitle: String?,
    val onClick: () -> Unit,
    val danger: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    variant: MoreVariant,
    onBack: () -> Unit,
    onProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onHierarchy: () -> Unit = {},
    onBilling: () -> Unit = {},
    onClients: () -> Unit = {},
    onExpenses: () -> Unit = {},
    onSamples: () -> Unit = {},
    onEdetail: () -> Unit = {},
    onRcpa: () -> Unit = {},
    onReports: () -> Unit = {},
    onInvite: () -> Unit = {},
    onHelp: () -> Unit = {},
    onLogout: () -> Unit,
) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current

    val items: List<MoreItem> = when (variant) {
        MoreVariant.FieldPerson -> listOf(
            MoreItem(Icons.Default.LocalHospital, "Clients", "Add doctor / chemist / stockist", onClients),
            MoreItem(Icons.Default.Receipt, "Expenses", "TA / DA / actuals", onExpenses),
            MoreItem(Icons.Default.CardGiftcard, "Samples", "Stock balance + distribute", onSamples),
            MoreItem(Icons.Default.Slideshow, "E-detailing", "Product decks for offline use", onEdetail),
            MoreItem(Icons.Default.BarChart, "RCPA", "Competitor audit at chemists", onRcpa),
            MoreItem(Icons.Default.Person, "My profile", user?.email ?: "View account", onProfile),
            MoreItem(Icons.Default.Help, "Help & support", "FAQs, contact admin", onHelp),
            MoreItem(Icons.AutoMirrored.Filled.Logout, "Sign out", null, onLogout, danger = true),
        )
        MoreVariant.Manager -> listOf(
            MoreItem(Icons.Default.PersonSearch, "Joint working", "Log coaching for a rep", onReports),
            MoreItem(Icons.Default.Analytics, "Reports", "Attainment & spend charts", onReports),
            MoreItem(Icons.Default.Receipt, "My expenses", "Submit your own", onExpenses),
            MoreItem(Icons.Default.LocalHospital, "Clients", "Browse & approve", onClients),
            MoreItem(Icons.Default.Person, "My profile", user?.email ?: "View account", onProfile),
            MoreItem(Icons.Default.Help, "Help & support", "FAQs, contact admin", onHelp),
            MoreItem(Icons.AutoMirrored.Filled.Logout, "Sign out", null, onLogout, danger = true),
        )
        MoreVariant.Admin -> listOf(
            MoreItem(Icons.Default.PersonAdd, "Invite user", "Add a Field Person or Manager", onInvite),
            MoreItem(Icons.Default.CreditCard, "Billing", "Plan, invoices, GSTIN", onBilling),
            MoreItem(Icons.Default.AccountTree, "Hierarchy", "Org chart & reporting lines", onHierarchy),
            MoreItem(Icons.Default.Analytics, "Reports", "Company-wide KPIs & charts", onReports),
            MoreItem(Icons.Default.LocalHospital, "Clients master", "Add / approve clients", onClients),
            MoreItem(Icons.Default.Settings, "Settings", "Company profile, TA/DA rates", onSettings),
            MoreItem(Icons.Default.Person, "My profile", user?.email ?: "View account", onProfile),
            MoreItem(Icons.Default.Help, "Help & support", "FAQs", onHelp),
            MoreItem(Icons.AutoMirrored.Filled.Logout, "Sign out", null, onLogout, danger = true),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("More") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (user != null) {
                item { ProfileCard(user.name, user.email, user.role) }
            }
            items(items, key = { it.label }) { MoreRow(it) }
        }
    }
}

@Composable
private fun ProfileCard(name: String, email: String, role: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(
                when (role) {
                    "ADMIN" -> "Admin"
                    "SENIOR_MANAGER" -> "Senior"
                    "REGIONAL_MANAGER" -> "Regional"
                    else -> "Field"
                },
                PillTone.Primary,
            )
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { item.onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(
                        if (item.danger) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    item.icon, null,
                    tint = if (item.danger) MaterialTheme.colorScheme.onErrorContainer
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (!item.subtitle.isNullOrBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 1000)
@Composable
private fun MoreFieldPreview() {
    FieldPharmaTheme {
        MoreScreen(variant = MoreVariant.FieldPerson, onBack = {}, onLogout = {})
    }
}
