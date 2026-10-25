package com.baseras.fieldpharma.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.auth.AuthEvent
import com.baseras.fieldpharma.ui.clients.ClientsScreen
import com.baseras.fieldpharma.ui.clients.NewClientScreen
import com.baseras.fieldpharma.ui.edetail.DecksScreen
import com.baseras.fieldpharma.ui.edetail.DeckViewerScreen
import com.baseras.fieldpharma.ui.expense.ExpensesScreen
import com.baseras.fieldpharma.ui.expense.NewExpenseScreen
import com.baseras.fieldpharma.ui.help.HelpScreen
import com.baseras.fieldpharma.ui.invite.InviteUserScreen
import com.baseras.fieldpharma.ui.profile.ProfileScreen
import com.baseras.fieldpharma.ui.settings.CompanyProfileScreen
import com.baseras.fieldpharma.ui.settings.ExpensePoliciesScreen
import com.baseras.fieldpharma.ui.settings.SettingsScreen
import com.baseras.fieldpharma.ui.settings.SettingsVariant
import com.baseras.fieldpharma.ui.approvals.ApprovalFilter
import com.baseras.fieldpharma.ui.approvals.ApprovalsScreen
import com.baseras.fieldpharma.ui.billing.BillingScreen
import com.baseras.fieldpharma.ui.home.HomeRouter
import com.baseras.fieldpharma.ui.more.MoreScreen
import com.baseras.fieldpharma.ui.more.MoreVariant
import com.baseras.fieldpharma.ui.plan.AssignPlanScreen
import com.baseras.fieldpharma.ui.reports.DcrDownloadScreen
import com.baseras.fieldpharma.ui.reports.ReportsScreen
import com.baseras.fieldpharma.ui.dcr.DcrDayScreen
import com.baseras.fieldpharma.ui.team.TeamScreen
import com.baseras.fieldpharma.ui.locked.LockedScreen
import com.baseras.fieldpharma.ui.login.LoginScreen
import com.baseras.fieldpharma.ui.rcpa.NewRcpaScreen
import com.baseras.fieldpharma.ui.rcpa.RcpaScreen
import com.baseras.fieldpharma.ui.sample.SamplesScreen
import com.baseras.fieldpharma.ui.tour.TourPlansScreen
import com.baseras.fieldpharma.ui.visit.PickClientScreen
import com.baseras.fieldpharma.ui.visit.VisitFlow
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val LOCKED = "locked"
    const val HOME = "home"
    const val CLIENTS = "clients"
    const val CLIENT_NEW = "clients/new"
    const val TOUR_PLANS = "tour-plans"
    const val VISITS = "visits?clientId={clientId}"
    const val PICK_CLIENT = "visits/pick-client"
    const val EXPENSES = "expenses"
    const val EXPENSE_NEW = "expenses/new"
    const val SAMPLES = "samples"
    const val EDETAIL = "edetail"
    const val DECK_VIEWER = "edetail/{deckId}"
    const val RCPA = "rcpa"
    const val RCPA_NEW = "rcpa/new"
    const val APPROVALS = "approvals?filter={filter}"
    const val TEAM = "team"
    const val BILLING = "billing"
    const val MORE = "more"
    const val REPORTS = "reports"
    const val INVITE = "invite"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val HELP = "help"
    const val COMPANY_PROFILE = "settings/company"
    const val EXPENSE_POLICIES = "settings/expense-policies"
    const val ASSIGN_PLAN = "assign-plan"
    const val DCR_DOWNLOAD = "reports/dcr"
    const val DCR_DAY = "dcr/day"

    fun approvals(filter: String? = null) =
        if (filter == null) "approvals?filter=" else "approvals?filter=$filter"

    fun visits(clientId: String? = null) =
        if (clientId == null) "visits?clientId=" else "visits?clientId=$clientId"

    fun deck(id: String) = "edetail/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val app = FieldPharmaApp.instance
    val billing by app.billingStore.state.collectAsState()

    val start = when {
        !app.authRepo.isAuthenticated -> Routes.LOGIN
        billing.isLocked -> Routes.LOCKED
        else -> Routes.HOME
    }

    // Refresh billing on launch for already-signed-in users so a tenant
    // locked between sessions is caught.
    LaunchedEffect(Unit) {
        if (app.authRepo.isAuthenticated) {
            runCatching { app.billingRepo.refresh() }
        }
    }

    // If billing flips to LOCKED while the app is open, boot into the lock
    // screen; if it unlocks, go home.
    LaunchedEffect(billing.status) {
        if (!app.authRepo.isAuthenticated) return@LaunchedEffect
        val current = nav.currentDestination?.route
        if (billing.isLocked && current != Routes.LOCKED && current != Routes.LOGIN) {
            nav.navigate(Routes.LOCKED) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else if (!billing.isLocked && current == Routes.LOCKED) {
            nav.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        app.authStore.events.collect { event ->
            when (event) {
                AuthEvent.SessionExpired -> {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onSignedIn = {
                // After login the LaunchedEffect(billing.status) above will
                // route to LOCKED if needed; default is HOME.
                val next = if (app.billingStore.state.value.isLocked) Routes.LOCKED else Routes.HOME
                nav.navigate(next) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.LOCKED) {
            val coScope = rememberCoroutineScope()
            LockedScreen(
                status = billing.status,
                onRetry = {
                    coScope.launch { app.billingRepo.refresh() }
                },
                onSignOut = {
                    app.authRepo.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeRouter(
                onClients = { nav.navigate(Routes.CLIENTS) },
                onTourPlans = { nav.navigate(Routes.TOUR_PLANS) },
                onVisits = { nav.navigate(Routes.visits()) },
                onExpenses = { nav.navigate(Routes.EXPENSES) },
                onSamples = { nav.navigate(Routes.SAMPLES) },
                onEdetail = { nav.navigate(Routes.EDETAIL) },
                onRcpa = { nav.navigate(Routes.RCPA) },
                onApprovals = { nav.navigate(Routes.approvals()) },
                onTeam = { nav.navigate(Routes.TEAM) },
                onBilling = { nav.navigate(Routes.BILLING) },
                onReports = { nav.navigate(Routes.REPORTS) },
                onInvite = { nav.navigate(Routes.INVITE) },
                onAssignPlan = { nav.navigate(Routes.ASSIGN_PLAN) },
                onJointWork = { nav.navigate(Routes.approvals("joint")) },
                onExpensePolicies = { nav.navigate(Routes.EXPENSE_POLICIES) },
                onMore = { nav.navigate(Routes.MORE) },
                onLogout = {
                    app.authRepo.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
                },
            )
        }
        composable(
            route = Routes.APPROVALS,
            arguments = listOf(navArgument("filter") {
                type = NavType.StringType
                defaultValue = ""
                nullable = true
            }),
        ) { entry ->
            val filterArg = entry.arguments?.getString("filter").orEmpty()
            val initialFilter = when (filterArg) {
                "joint" -> ApprovalFilter.JointWork
                "plans" -> ApprovalFilter.TourPlans
                "expenses" -> ApprovalFilter.Expenses
                else -> ApprovalFilter.All
            }
            ApprovalsScreen(onBack = { nav.popBackStack() }, initialFilter = initialFilter)
        }
        composable(Routes.TEAM) {
            TeamScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.BILLING) {
            BillingScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORTS) {
            ReportsScreen(
                onBack = { nav.popBackStack() },
                onDcrDownload = { nav.navigate(Routes.DCR_DOWNLOAD) },
            )
        }
        composable(Routes.MORE) {
            val role = app.authRepo.current?.role ?: "FIELD_PERSON"
            val variant = when (role) {
                "ADMIN" -> MoreVariant.Admin
                "SENIOR_MANAGER", "REGIONAL_MANAGER" -> MoreVariant.Manager
                else -> MoreVariant.FieldPerson
            }
            MoreScreen(
                variant = variant,
                onBack = { nav.popBackStack() },
                onProfile = { nav.navigate(Routes.PROFILE) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onHierarchy = { nav.navigate(Routes.TEAM) },
                onBilling = { nav.navigate(Routes.BILLING) },
                onClients = { nav.navigate(Routes.CLIENTS) },
                onExpenses = { nav.navigate(Routes.EXPENSES) },
                onSamples = { nav.navigate(Routes.SAMPLES) },
                onEdetail = { nav.navigate(Routes.EDETAIL) },
                onRcpa = { nav.navigate(Routes.RCPA) },
                onReports = { nav.navigate(Routes.REPORTS) },
                onInvite = { nav.navigate(Routes.INVITE) },
                onHelp = { nav.navigate(Routes.HELP) },
                onDcrDay = { nav.navigate(Routes.DCR_DAY) },
                onDcrDownload = { nav.navigate(Routes.DCR_DOWNLOAD) },
                onLogout = {
                    app.authRepo.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.INVITE) {
            InviteUserScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val role = app.authRepo.current?.role ?: "FIELD_PERSON"
            val variant = when (role) {
                "ADMIN" -> SettingsVariant.Admin
                "SENIOR_MANAGER", "REGIONAL_MANAGER" -> SettingsVariant.Manager
                else -> SettingsVariant.FieldPerson
            }
            SettingsScreen(
                variant = variant,
                onBack = { nav.popBackStack() },
                onCompanyProfile = { nav.navigate(Routes.COMPANY_PROFILE) },
                onExpensePolicies = { nav.navigate(Routes.EXPENSE_POLICIES) },
                onHierarchy = { nav.navigate(Routes.TEAM) },
                onBilling = { nav.navigate(Routes.BILLING) },
            )
        }
        composable(Routes.COMPANY_PROFILE) {
            CompanyProfileScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.EXPENSE_POLICIES) {
            ExpensePoliciesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ASSIGN_PLAN) {
            AssignPlanScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DCR_DOWNLOAD) {
            DcrDownloadScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DCR_DAY) {
            DcrDayScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { nav.popBackStack() },
                onLogout = {
                    app.authRepo.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CLIENTS) {
            ClientsScreen(
                onBack = { nav.popBackStack() },
                onCreate = { nav.navigate(Routes.CLIENT_NEW) },
                onClick = { },
            )
        }
        composable(Routes.CLIENT_NEW) {
            NewClientScreen(
                onBack = { nav.popBackStack() },
                onCreated = { nav.popBackStack() },
            )
        }
        composable(Routes.TOUR_PLANS) {
            TourPlansScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.VISITS,
            arguments = listOf(navArgument("clientId") {
                type = NavType.StringType
                defaultValue = ""
                nullable = true
            }),
        ) { entry ->
            val clientId = entry.arguments?.getString("clientId")?.takeIf { it.isNotEmpty() }
            VisitFlow(
                checkInClientId = clientId,
                onBack = { nav.popBackStack() },
                onPickClient = { nav.navigate(Routes.PICK_CLIENT) },
            )
        }
        composable(Routes.PICK_CLIENT) {
            PickClientScreen(
                onBack = { nav.popBackStack() },
                onPicked = { client ->
                    nav.navigate(Routes.visits(client.id)) {
                        popUpTo(Routes.VISITS) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.EXPENSES) {
            ExpensesScreen(
                onBack = { nav.popBackStack() },
                onNew = { nav.navigate(Routes.EXPENSE_NEW) },
            )
        }
        composable(Routes.EXPENSE_NEW) {
            NewExpenseScreen(
                onBack = { nav.popBackStack() },
                onCreated = { nav.popBackStack() },
            )
        }
        composable(Routes.SAMPLES) {
            SamplesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.EDETAIL) {
            DecksScreen(
                onBack = { nav.popBackStack() },
                onOpen = { deck -> nav.navigate(Routes.deck(deck.id)) },
            )
        }
        composable(
            route = Routes.DECK_VIEWER,
            arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
        ) { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            DeckViewerScreen(deckId = deckId, onClose = { nav.popBackStack() })
        }
        composable(Routes.RCPA) {
            RcpaScreen(
                onBack = { nav.popBackStack() },
                onNew = { nav.navigate(Routes.RCPA_NEW) },
            )
        }
        composable(Routes.RCPA_NEW) {
            NewRcpaScreen(
                onBack = { nav.popBackStack() },
                onCreated = { nav.popBackStack() },
            )
        }
    }
}
