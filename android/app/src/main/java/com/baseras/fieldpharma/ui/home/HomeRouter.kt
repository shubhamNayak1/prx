package com.baseras.fieldpharma.ui.home

import androidx.compose.runtime.Composable
import com.baseras.fieldpharma.FieldPharmaApp

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
    onReports: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val role = FieldPharmaApp.instance.authRepo.current?.role ?: "FIELD_PERSON"
    when (role) {
        "ADMIN" -> AdminHomeScreen(
            onPeople = onTeam,       // Admin → "People" opens team / users
            onBilling = onBilling,
            onMore = onMore,
            onLogout = onLogout,
        )
        "SENIOR_MANAGER" -> SeniorManagerHomeScreen(
            onApprovals = onApprovals,
            onRegions = onTeam,
            onReports = onReports,
            onMore = onMore,
            onLogout = onLogout,
        )
        "REGIONAL_MANAGER" -> RegionalManagerHomeScreen(
            onApprovals = onApprovals,
            onTeam = onTeam,
            onMore = onMore,
            onLogout = onLogout,
        )
        else -> HomeScreen(
            onAttendance = onAttendance,
            onClients = onClients,
            onTourPlans = onTourPlans,
            onVisits = onVisits,
            onExpenses = onExpenses,
            onSamples = onSamples,
            onEdetail = onEdetail,
            onRcpa = onRcpa,
            onMore = onMore,
            onLogout = onLogout,
        )
    }
}
