package com.carecompanion.presentation.navigation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carecompanion.presentation.ui.home.HomeScreen
import com.carecompanion.presentation.ui.recall.RecallBiometricScreen
import com.carecompanion.presentation.ui.recall.RecallScreen
import com.carecompanion.presentation.ui.verify.VerifyScreen
import com.carecompanion.presentation.ui.sync.SyncScreen
import com.carecompanion.presentation.ui.settings.SettingsScreen
import com.carecompanion.presentation.ui.patient.PatientScreen
import com.carecompanion.presentation.ui.patient.PatientListScreen
import com.carecompanion.presentation.ui.services.IITScreen
import com.carecompanion.presentation.ui.services.ServicesScreen
import com.carecompanion.presentation.viewmodels.SharedViewModel
sealed class Screen(val route: String) {
    object Home:Screen("home")
    object Recall:Screen("recall"); object RecallBiometric:Screen("recall_biometric")
    object Verify:Screen("verify"); object Sync:Screen("sync")
    object Settings:Screen("settings")
    object PatientList:Screen("patient_list")
    object Services:Screen("services")
    object IIT:Screen("iit")
    object PatientProfile:Screen("patient/{patientId}") { fun createRoute(id:String)="patient/$id" }
}
@Composable
fun AppNavHost(navController: NavHostController=rememberNavController(), sharedViewModel: SharedViewModel, modifier: Modifier=Modifier) {
    NavHost(navController=navController, startDestination=Screen.Home.route, modifier=modifier) {
        composable(Screen.Home.route)           { HomeScreen(navController, sharedViewModel) }
        composable(Screen.RecallBiometric.route){ RecallBiometricScreen(navController) }
        composable(Screen.Recall.route)         { RecallScreen(navController,sharedViewModel) }
        composable(Screen.Verify.route)         { VerifyScreen(navController,sharedViewModel) }
        composable(Screen.Sync.route)           { SyncScreen(navController,sharedViewModel) }
        composable(Screen.Settings.route)       { SettingsScreen(navController,sharedViewModel) }
        composable(Screen.PatientList.route)    { PatientListScreen(navController,sharedViewModel) }
        composable(Screen.Services.route)       { ServicesScreen(navController) }
        composable(Screen.IIT.route)            { IITScreen(navController) }
        composable(Screen.PatientProfile.route) { back ->
            val id = back.arguments?.getString("patientId") ?: return@composable
            PatientScreen(navController,id,sharedViewModel)
        }
    }
}