package com.baseras.fieldpharma.ui.home

import androidx.compose.runtime.Composable
import com.baseras.fieldpharma.FieldPharmaApp

@Composable
fun HomeRouter(
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
    onInvite: () -> Unit,
    onAssignPlan: () -> Unit,
    onJointWork: () -> Unit,
    onExpensePolicies: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit,
) {
    val role = FieldPharmaApp.instance.authRepo.current?.role ?: "FIELD_PERSON"
    when (role) {
        "ADMIN" -> AdminHomeScreen(
            onPeople = onTeam,
            onInvite = onInvite,
            onBilling = onBilling,
            onExpensePolicies = onExpensePolicies,
            onMore = onMore,
            onLogout = onLogout,
        )
        "SENIOR_MANAGER" -> SeniorManagerHomeScreen(
            onApprovals = onApprovals,
            onRegions = onTeam,
            onReports = onReports,
            onJointWork = onJointWork,
            onMore = onMore,
            onLogout = onLogout,
        )
        "REGIONAL_MANAGER" -> RegionalManagerHomeScreen(
            onApprovals = onApprovals,
            onTeam = onTeam,
            onAssignPlan = onAssignPlan,
            onJointWork = onJointWork,
            onMore = onMore,
            onLogout = onLogout,
        )
        else -> HomeScreen(
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
