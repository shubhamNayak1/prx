package com.baseras.fieldpharma.ui.home

import androidx.compose.runtime.Composable
import com.baseras.fieldpharma.FieldPharmaApp

/**
 * Picks the right home screen for the signed-in user's role. Each role gets
 * a different information architecture — attendance-first for Field, team
 * rollup for managers, company KPIs + billing for Admin.
 */
@Composable
fun HomeRouter(
    onAttendance: () -> Unit,
    onClients: () -> Unit,
    onTourPlans: () -> Unit,
    onVisits: () -> Unit,
    onExpenses: () -> Unit,
    onSamples: () -> Unit,
    onEdetail: () -> Unit,
    onRcpa: () -> Unit,
    onApprovals: () -> Unit,
    onTeam: () -> Unit,
    onBilling: () -> Unit,
    onLogout: () -> Unit,
) {
    val role = FieldPharmaApp.instance.authRepo.current?.role ?: "FIELD_PERSON"
    when (role) {
        "ADMIN" -> AdminHomeScreen(
            onPeople = { /* TODO: users list on mobile */ onClients() },
            onBilling = onBilling,
            onMore = { /* TODO: settings */ },
            onLogout = onLogout,
        )
        "SENIOR_MANAGER" -> SeniorManagerHomeScreen(
            onApprovals = onApprovals,
            onRegions = onTeam,              // Regions list reuses the team screen
            onReports = { /* TODO: reports */ },
            onMore = { /* TODO */ },
            onLogout = onLogout,
        )
        "REGIONAL_MANAGER" -> RegionalManagerHomeScreen(
            onApprovals = onApprovals,
            onTeam = onTeam,
            onMore = { /* TODO */ },
            onLogout = onLogout,
        )
        else -> HomeScreen(   // FIELD_PERSON — the full operational dashboard
            onAttendance = onAttendance,
            onClients = onClients,
            onTourPlans = onTourPlans,
            onVisits = onVisits,
            onExpenses = onExpenses,
            onSamples = onSamples,
            onEdetail = onEdetail,
            onRcpa = onRcpa,
            onLogout = onLogout,
        )
    }
}
