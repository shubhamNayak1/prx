package com.baseras.fieldpharma.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val app = FieldPharmaApp.instance
    val user = app.authRepo.current
    val role = user?.role ?: "FIELD_PERSON"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("My profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (user?.name ?: "?").take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user?.name ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        StatusPill(
                            when (role) {
                                "ADMIN" -> "Admin"
                                "SENIOR_MANAGER" -> "Senior Manager"
                                "REGIONAL_MANAGER" -> "Regional Manager"
                                else -> "Field Person"
                            },
                            PillTone.Primary,
                        )
                    }
                }
            }

            // Details
            SectionHeader("Account")
            InfoRow(Icons.Default.Email, "Email", user?.email ?: "—")
            InfoRow(Icons.Default.Badge, "Role", when (role) {
                "ADMIN" -> "Admin"
                "SENIOR_MANAGER" -> "Senior Manager"
                "REGIONAL_MANAGER" -> "Regional Manager"
                else -> "Field Person"
            })
            InfoRow(Icons.Default.Lock, "Change password", "Contact your admin for now")

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign out", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC, heightDp = 900)
@Composable
private fun ProfilePreview() {
    FieldPharmaTheme { ProfileScreen(onBack = {}, onLogout = {}) }
}
