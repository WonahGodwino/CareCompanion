package com.carecompanion.presentation.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.BuildConfig
import com.carecompanion.data.database.entities.Facility
import com.carecompanion.presentation.viewmodels.SettingsViewModel
import com.carecompanion.presentation.viewmodels.SharedViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showFacilityPickerManual by remember { mutableStateOf(false) }

    // Auto-clear success banner after 3 seconds
    LaunchedEffect(uiState.reAuthSuccess) {
        if (uiState.reAuthSuccess) {
            delay(3000)
            viewModel.clearReAuthSuccess()
        }
    }

    // needsFacilitySelection drives the picker directly from ViewModel state
    val showFacilityPicker = uiState.needsFacilitySelection || showFacilityPickerManual

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── WINCO Server ────────────────────────────────────────
            Text("WINCO Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = uiState.emrUrl, onValueChange = viewModel::onEmrUrlChanged,
                label = { Text("WINCO Server URL") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("http://192.168.x.x:5000/") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true
            )
            OutlinedTextField(
                value = uiState.emrUsername, onValueChange = viewModel::onEmrUsernameChanged,
                label = { Text("Username") }, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.emrPassword, onValueChange = viewModel::onEmrPasswordChanged,
                label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::saveSettings,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("Save") }
                }
                OutlinedButton(
                    onClick = viewModel::reAuthenticate,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isReAuthLoading
                ) {
                    if (uiState.isReAuthLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Re-Auth") }
                }
            }

            if (uiState.isSaved || uiState.reAuthSuccess) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (uiState.reAuthSuccess) "Authentication successful! Token refreshed."
                            else "Settings saved"
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Active Facility ─────────────────────────────────────
            Text("Active Facility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (uiState.activeFacilityName.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalHospital, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.activeFacilityName, fontWeight = FontWeight.Medium)
                        }
                        if (uiState.facilities.isNotEmpty()) {
                            TextButton(onClick = { showFacilityPickerManual = true }) { Text(if (uiState.facilities.size > 1) "Switch" else "View") }
                        }
                    }
                }
            } else {
                Text("No facility selected. Re-authenticate to load facilities.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            HorizontalDivider()

            // ── Build Info ────────────────────────────────────────
            Text("Build Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Build UTC: ${BuildConfig.BUILD_TIME_UTC}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // ── Scanner ─────────────────────────────────────────────
            Text("Biometric Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val scanners = listOf("SecuGen", "DigitalPersona", "Mantra", "Other")
            scanners.forEach { scanner ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = uiState.scannerType == scanner, onClick = { viewModel.onScannerTypeChanged(scanner) })
                    Text(scanner)
                }
            }

            HorizontalDivider()

            // ── Notifications ─────────────────────────────────────
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Permission launcher for Android 13+
            val notifPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> viewModel.toggleNotifications(granted) }

            // Master toggle
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (uiState.notificationsEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Notifications", fontWeight = FontWeight.Medium)
                        Text(
                            "Daily patient summary and clinical alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleNotifications(enabled)
                            }
                        }
                    )
                }
            }

            // Per-module toggles (only enabled when master switch is on)
            if (uiState.notificationsEnabled) {
                val moduleToggles = listOf(
                    Triple("Daily Digest",           uiState.dailyDigestEnabled,  viewModel::toggleDailyDigest),
                    Triple("ART Refills",            uiState.artRefillsEnabled,   viewModel::toggleArtRefills),
                    Triple("Missed Appointments",    uiState.missedApptsEnabled,  viewModel::toggleMissedAppts),
                    Triple("Viral Load",             uiState.viralLoadEnabled,    viewModel::toggleViralLoad),
                    Triple("TPT",                    uiState.tptEnabled,          viewModel::toggleTpt),
                    Triple("AHD Alerts",             uiState.ahdEnabled,          viewModel::toggleAhd)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        moduleToggles.forEachIndexed { index, (label, checked, toggle) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = checked,
                                    onCheckedChange = toggle,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            if (index < moduleToggles.lastIndex) {
                                HorizontalDivider(
                                    modifier  = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color     = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    "\u2022 Daily Digest fires at 08:00 AM with your active patient count.\n" +
                    "\u2022 Service module alerts (ART Refills, Missed Appointments, Viral Load, TPT, AHD) " +
                    "will activate automatically when the full ART data module is integrated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ── Danger zone ─────────────────────────────────────
            Text("Danger Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear All App Data")
            }
        }
    }

    // Facility picker dialog — shown either automatically after re-auth or manually via Switch/View
    if (showFacilityPicker && uiState.facilities.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearNeedsFacilitySelection()
                showFacilityPickerManual = false
            },
            title = { Text("Select Facility") },
            text = {
                Column {
                    uiState.facilities.forEach { facility ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = facility.id == uiState.activeFacilityId,
                                onClick = {
                                    viewModel.switchFacility(facility)
                                    viewModel.clearNeedsFacilitySelection()
                                    showFacilityPickerManual = false
                                }
                            )
                            Column {
                                Text(facility.name, fontWeight = FontWeight.Medium)
                                if (!facility.lga.isNullOrBlank()) {
                                    Text("${facility.lga}, ${facility.state ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearNeedsFacilitySelection()
                    showFacilityPickerManual = false
                }) { Text("Cancel") }
            }
        )
    }

    // Clear confirm dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All App Data?") },
            text = { Text("This will permanently delete all synced patients, biometrics, settings, and preferences. The app will need to be reconfigured.\n\nThis action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllData(); showClearConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}
