package com.carecompanion.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carecompanion.presentation.ui.auth.LoginScreen
import com.carecompanion.presentation.ui.home.HomeScreen
import com.carecompanion.presentation.ui.insights.AiInsightsScreen
import com.carecompanion.presentation.ui.insights.ModelValidationScreen
import com.carecompanion.presentation.ui.insights.ReminderLogScreen
import com.carecompanion.presentation.ui.patient.PatientListScreen
import com.carecompanion.presentation.ui.patient.PatientScreen
import com.carecompanion.presentation.ui.patient.UnregisteredBiometricScreen
import com.carecompanion.presentation.ui.recall.RecallBiometricScreen
import com.carecompanion.presentation.ui.recall.RecallScreen
import com.carecompanion.presentation.ui.services.ArtRefillScreen
import com.carecompanion.presentation.ui.services.EacWorklistScreen
import com.carecompanion.presentation.ui.services.PmtctWorklistScreen
import com.carecompanion.presentation.ui.services.IITScreen
import com.carecompanion.presentation.ui.services.MissedApptScreen
import com.carecompanion.presentation.ui.services.ServicesScreen
import com.carecompanion.presentation.ui.services.TptScreen
import com.carecompanion.presentation.ui.services.ViralLoadScreen
import com.carecompanion.presentation.ui.services.VlCascadeScreen
import com.carecompanion.presentation.ui.settings.SettingsScreen
import com.carecompanion.presentation.ui.sync.SyncScreen
import com.carecompanion.presentation.ui.verify.VerifyScreen
import com.carecompanion.presentation.ui.worklist.TodayWorklistScreen
import com.carecompanion.presentation.viewmodels.SharedViewModel

sealed class Screen(val route: String) {
    object Login           : Screen("login")
    object Home            : Screen("home")
    object Recall          : Screen("recall")
    object RecallBiometric : Screen("recall_biometric")
    object Verify          : Screen("verify")
    object Sync            : Screen("sync")
    object Settings        : Screen("settings")
    object PatientList     : Screen("patient_list")
    object Services        : Screen("services")
    object IIT             : Screen("iit")
    object MissedAppt      : Screen("missed_appt")
    object ArtRefill       : Screen("art_refill")
    object ViralLoad       : Screen("viral_load")
    object TodayWorklist   : Screen("today_worklist")
    object VlCascade       : Screen("vl_cascade")
    object AiInsights      : Screen("ai_insights")
    object ReminderLog     : Screen("reminder_log")
    object ModelValidation : Screen("model_validation")
    object Tpt             : Screen("tpt")
    object EacWorklist     : Screen("eac_worklist")
    object PmtctWorklist   : Screen("pmtct_worklist")
    object NoBiometric     : Screen("no_biometric")
    object PatientProfile  : Screen("patient/{patientId}") {
        fun createRoute(id: String) = "patient/$id"
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    sharedViewModel: SharedViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Login.route,
        modifier         = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route)            { HomeScreen(navController, sharedViewModel) }
        composable(Screen.RecallBiometric.route) { RecallBiometricScreen(navController, sharedViewModel) }
        composable(Screen.Recall.route)          { RecallScreen(navController, sharedViewModel) }
        composable(Screen.Verify.route)          { VerifyScreen(navController, sharedViewModel) }
        composable(Screen.Sync.route)            { SyncScreen(navController, sharedViewModel) }
        composable(Screen.Settings.route)        { SettingsScreen(navController, sharedViewModel) }
        composable(Screen.PatientList.route)     { PatientListScreen(navController, sharedViewModel) }
        composable(Screen.Services.route)        { ServicesScreen(navController) }
        composable(Screen.IIT.route)             { IITScreen(navController) }
        composable(Screen.MissedAppt.route)      { MissedApptScreen(navController) }
        composable(Screen.ArtRefill.route)       { ArtRefillScreen(navController) }
        composable(Screen.ViralLoad.route)       { ViralLoadScreen(navController) }
        composable(Screen.TodayWorklist.route)   { TodayWorklistScreen(navController, sharedViewModel) }
        composable(Screen.VlCascade.route)       { VlCascadeScreen(navController) }
        composable(Screen.AiInsights.route)      { AiInsightsScreen(navController) }
        composable(Screen.ReminderLog.route)     { ReminderLogScreen(navController) }
        composable(Screen.ModelValidation.route) { ModelValidationScreen(navController) }
        composable(Screen.Tpt.route)             { TptScreen(navController) }
        composable(Screen.EacWorklist.route)     { EacWorklistScreen(navController) }
        composable(Screen.PmtctWorklist.route)   { PmtctWorklistScreen(navController) }
        composable(Screen.NoBiometric.route)     { UnregisteredBiometricScreen(navController) }
        composable(Screen.PatientProfile.route) { back ->
            val id = back.arguments?.getString("patientId") ?: return@composable
            PatientScreen(navController, id, sharedViewModel)
        }
    }
}
