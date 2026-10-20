package com.baseras.fieldpharma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

enum class TeamMemberStatus { ActiveToday, NotPunched, OffField, Flagged }

data class TeamMember(
    val name: String,
    val roleLabel: String,     // e.g. "Field · Delhi" or "Regional Manager · West"
    val status: TeamMemberStatus,
    /** For Field: "4 / 10 calls". For Regional/Senior Manager: "6 reps active". */
    val progressLine: String,
)

@Composable
fun TeamMemberItem(
    member: TeamMember,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    member.roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusChipFor(member.status)
                Spacer(Modifier.height(4.dp))
                Text(
                    member.progressLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChipFor(status: TeamMemberStatus) {
    when (status) {
        TeamMemberStatus.ActiveToday -> StatusPill("Active", PillTone.Success)
        TeamMemberStatus.NotPunched -> StatusPill("Not in", PillTone.Warn)
        TeamMemberStatus.OffField -> StatusPill("Off", PillTone.Neutral)
        TeamMemberStatus.Flagged -> StatusPill("Flag", PillTone.Error)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun TeamListPreview() {
    FieldPharmaTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TeamMemberItem(TeamMember("Amit Singh", "Field · Delhi", TeamMemberStatus.ActiveToday, "4 / 10 calls"), {})
            TeamMemberItem(TeamMember("Priya Patel", "Field · Delhi", TeamMemberStatus.ActiveToday, "7 / 10 calls"), {})
            TeamMemberItem(TeamMember("Ravi Verma", "Field · NCR", TeamMemberStatus.NotPunched, "0 / 8 calls"), {})
            TeamMemberItem(TeamMember("Neha Gupta", "Field · Delhi", TeamMemberStatus.Flagged, "Out of radius ×2"), {})
        }
    }
}
