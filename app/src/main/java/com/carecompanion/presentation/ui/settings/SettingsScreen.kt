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
import androidx.compose.ui.platform.LocalContext
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
    val backfillStatus by viewModel.backfillStatus.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    // ...existing code...
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
            // ── Crash Log ─────────────────────────────────────────
            val context = LocalContext.current
            val crashLogFile = remember { com.carecompanion.utils.CrashLogger.getLogFile(context) }
            var showCrashLogDialog by remember { mutableStateOf(false) }
            if (crashLogFile != null) {
                            // ── Biometric Hash Backfill ─────────────────────────────
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Biometric Consistency Check", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Ensure all enrolled biometrics have hashes and required metadata. Run this after upgrades or data imports.", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { viewModel.triggerBiometricHashBackfill() }) {
                                        Icon(Icons.Default.Build, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Run Biometric Hash Backfill")
                                    }
                                    if (backfillStatus != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(backfillStatus ?: "", color = MaterialTheme.colorScheme.primary)
                                        LaunchedEffect(backfillStatus) {
                                            if (backfillStatus != null) {
                                                delay(4000)
                                                viewModel.clearBackfillStatus()
                                            }
                                        }
                                    }
                                }
                            }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Crash Log Available", fontWeight = FontWeight.SemiBold)
                        Text("A crash log was found at Documents/care_companion_crash_log.txt. You can share it for support or clear it.", style = MaterialTheme.typography.bodySmall)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".provider",
                                    crashLogFile
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Crash Log"))
                            }) {
                                Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Share Log")
                            }
                            OutlinedButton(onClick = { showCrashLogDialog = true }) {
                                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Clear Log")
                            }
                        }
                    }
                }
            }
            if (showCrashLogDialog) {
                AlertDialog(
                    onDismissRequest = { showCrashLogDialog = false },
                    title = { Text("Clear Crash Log?") },
                    text = { Text("Are you sure you want to delete the crash log file? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            crashLogFile?.delete()
                            showCrashLogDialog = false
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showCrashLogDialog = false }) { Text("Cancel") } }
                )
            }

            // ── Biometric Log Files ──────────────────────────────────
            var showBiometricLogDialog by remember { mutableStateOf(false) }
            val biometricLogPath = remember { com.carecompanion.biometric.BiometricFileLogger.logDirectoryPath() }
            val biometricLogFiles = remember { com.carecompanion.biometric.BiometricFileLogger.listLogFiles() }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Biometric Diagnostic Logs", fontWeight = FontWeight.Bold)
                    Text(
                        "Logs record every scan result, format detection, and matching decision. " +
                        "Use these to analyse identification accuracy.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Location: $biometricLogPath",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (biometricLogFiles.isEmpty()) {
                        Text("No log files yet — logs appear after the first scan.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("${biometricLogFiles.size} file(s): ${biometricLogFiles.map { it.name }.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showBiometricLogDialog = true }) {
                                Icon(Icons.Default.Article, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("View Latest")
                            }
                            OutlinedButton(onClick = {
                                val latestFile = biometricLogFiles.firstOrNull() ?: return@OutlinedButton
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".provider", latestFile
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Biometric Log"))
                            }) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
            if (showBiometricLogDialog) {
                var logContent by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    logContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.carecompanion.biometric.BiometricFileLogger.readLastLines(100)
                    }
                }
                AlertDialog(
                    onDismissRequest = { showBiometricLogDialog = false },
                    title = { Text("Biometric Log (last 100 lines)") },
                    text = {
                        if (logContent == null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else {
                            val scroll = androidx.compose.foundation.rememberScrollState()
                            Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(scroll)) {
                                Text(
                                    text = logContent ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showBiometricLogDialog = false }) { Text("Close") } }
                )
            }

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

            // ── Facility Location (feeds AI distance prediction) ────────────
            if (uiState.activeFacilityId > 0) {
                FacilityLocationCard(facilityId = uiState.activeFacilityId)
            }

            // ── Reminder Gateways (SMS / Email) ─────────────────────────────
            ReminderGatewayCard()

            // ── Automatic reminders + message templates ─────────────────────
            AutoReminderCard()


            // ── Match Threshold ─────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Biometric Match Threshold", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Adjust the minimum score required for a biometric match (0.5 = 50%, 0.9 = 90%). Lower for more matches, higher for stricter.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = uiState.matchThreshold,
                        onValueChange = { viewModel.onMatchThresholdChanged(it) },
                        valueRange = 0.5f..0.95f,
                        steps = 9
                    )
                    Text("Current: ${(uiState.matchThreshold * 100).toInt()}%", fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider()
            // ...existing code...

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

/**
 * Captures the active facility's GPS coordinates on-site and stores them locally.
 * These feed the distance-to-facility signal in the AI patient-monitoring engine.
 */
@Composable
private fun FacilityLocationCard(facilityId: Long) {
    val context = LocalContext.current
    var lat by remember(facilityId) { mutableStateOf(com.carecompanion.utils.SharedPreferencesHelper.getFacilityLatitude(facilityId)) }
    var lng by remember(facilityId) { mutableStateOf(com.carecompanion.utils.SharedPreferencesHelper.getFacilityLongitude(facilityId)) }
    var capturedAt by remember(facilityId) { mutableStateOf(com.carecompanion.utils.SharedPreferencesHelper.getFacilityLocationCapturedAt(facilityId)) }
    var capturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun capture() {
        capturing = true
        message = null
        com.carecompanion.utils.LocationCapture.current(
            context = context,
            onResult = { la, lo, acc ->
                com.carecompanion.utils.SharedPreferencesHelper.setFacilityLocation(facilityId, la, lo)
                lat = la; lng = lo; capturedAt = System.currentTimeMillis()
                capturing = false
                message = "Location saved" + (acc?.let { " (±${it.toInt()}m accuracy)" } ?: "")
            },
            onError = { err -> capturing = false; message = err }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture() else { capturing = false; message = "Location permission denied." }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Facility Location", fontWeight = FontWeight.Bold)
            }
            Text(
                "Capture the facility's GPS while standing inside the clinic. The AI uses this to " +
                "estimate each client's travel distance and flag those more likely to miss appointments.",
                style = MaterialTheme.typography.bodySmall
            )
            if (lat != null && lng != null) {
                Text("Saved: ${"%.5f".format(lat)}, ${"%.5f".format(lng)}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (capturedAt > 0) {
                    Text("Captured ${com.carecompanion.utils.DateUtils.formatDateTime(java.util.Date(capturedAt))}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Not set — distance prediction stays inactive until captured.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) capture()
                    else { capturing = true; permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                },
                enabled = !capturing
            ) {
                if (capturing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text("Locating…")
                } else {
                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (lat != null) "Update Location" else "Capture Current Location")
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/**
 * Configures the cloud reminder gateways (Termii SMS, SendGrid email) used by the
 * AI monitoring screen's "Send reminder" action. Keys are stored encrypted on-device.
 * For production, prefer proxying sends through the WINCO backend so keys never ship
 * in the APK.
 */
@Composable
private fun ReminderGatewayCard() {
    val sp = com.carecompanion.utils.SharedPreferencesHelper
    var smsEnabled by remember { mutableStateOf(sp.isSmsReminderEnabled()) }
    var termiiKey by remember { mutableStateOf(sp.getTermiiApiKey() ?: "") }
    var senderId by remember { mutableStateOf(sp.getTermiiSenderId()) }
    var emailEnabled by remember { mutableStateOf(sp.isEmailReminderEnabled()) }
    var sendgridKey by remember { mutableStateOf(sp.getSendGridApiKey() ?: "") }
    var fromEmail by remember { mutableStateOf(sp.getReminderEmailFrom() ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Appointment Reminders", fontWeight = FontWeight.Bold)
            }
            Text("Used by the AI monitoring screen to send patients neutral appointment reminders. " +
                "Messages never mention HIV/ART to protect confidentiality.",
                style = MaterialTheme.typography.bodySmall)

            // SMS (Termii)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("SMS via Termii", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = smsEnabled, onCheckedChange = { smsEnabled = it; sp.setSmsReminderEnabled(it) })
            }
            if (smsEnabled) {
                OutlinedTextField(
                    value = termiiKey, onValueChange = { termiiKey = it; sp.setTermiiApiKey(it) },
                    label = { Text("Termii API key") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = senderId, onValueChange = { senderId = it; sp.setTermiiSenderId(it) },
                    label = { Text("Sender ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            HorizontalDivider()

            // Email (SendGrid)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Email via SendGrid", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = emailEnabled, onCheckedChange = { emailEnabled = it; sp.setEmailReminderEnabled(it) })
            }
            if (emailEnabled) {
                OutlinedTextField(
                    value = sendgridKey, onValueChange = { sendgridKey = it; sp.setSendGridApiKey(it) },
                    label = { Text("SendGrid API key") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = fromEmail, onValueChange = { fromEmail = it; sp.setReminderEmailFrom(it) },
                    label = { Text("From email address") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Text("Note: patient email is not yet synced, so email targets activate once an email field is added.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Controls automatic reminder sending (background WorkManager job) and lets the clinic
 * compose & save a reusable message template per reminder type. Templates support the
 * placeholders {name}, {date}, {facility}, {days}.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoReminderCard() {
    val context = LocalContext.current
    val sp = com.carecompanion.utils.SharedPreferencesHelper
    var autoEnabled by remember { mutableStateOf(sp.isAutoReminderEnabled()) }
    var intervalHours by remember { mutableStateOf(sp.getAutoReminderIntervalHours()) }
    var audience by remember {
        mutableStateOf(com.carecompanion.data.reminder.ReminderAudience.fromName(sp.getAutoReminderAudience()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Automatic Reminders", fontWeight = FontWeight.Bold)
            }
            Text("The AI decides who to remind and when — earlier and more often for clients " +
                "predicted to default, a single courtesy reminder for reliable ones. You don't set " +
                "the schedule; just turn it on and write the wording below.",
                style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Let the AI send reminders", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(
                    checked = autoEnabled,
                    onCheckedChange = { on ->
                        autoEnabled = on
                        sp.setAutoReminderEnabled(on)
                        if (on) com.carecompanion.data.reminder.ReminderWorker.enable(context, intervalHours.toLong())
                        else com.carecompanion.data.reminder.ReminderWorker.cancel(context)
                    }
                )
            }

            if (autoEnabled) {
                Text("Who to remind", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.carecompanion.data.reminder.ReminderAudience.entries.forEach { a ->
                        FilterChip(
                            selected = audience == a,
                            onClick = { audience = a; sp.setAutoReminderAudience(a.name) },
                            label = { Text(a.label) }
                        )
                    }
                }
                Text(audience.description,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(4.dp))
                Text("How often the AI checks", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 24).forEach { h ->
                        FilterChip(
                            selected = intervalHours == h,
                            onClick = {
                                intervalHours = h
                                sp.setAutoReminderIntervalHours(h)
                                com.carecompanion.data.reminder.ReminderWorker.scheduleNext(context, h.toLong())
                            },
                            label = { Text("Every ${h}h") }
                        )
                    }
                }
            }

            HorizontalDivider()
            Text("Message templates", fontWeight = FontWeight.Medium)
            Text("Placeholders: ${com.carecompanion.data.messaging.ReminderType.PLACEHOLDERS.joinToString(" ")}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            com.carecompanion.data.messaging.ReminderType.values().forEach { type ->
                TemplateEditor(type)
            }
        }
    }
}

@Composable
private fun TemplateEditor(type: com.carecompanion.data.messaging.ReminderType) {
    val sp = com.carecompanion.utils.SharedPreferencesHelper
    var text by remember(type) { mutableStateOf(com.carecompanion.data.messaging.ReminderTemplates.templateFor(type)) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(type.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; sp.setReminderTemplate(type.key, it) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            textStyle = MaterialTheme.typography.bodySmall
        )
        TextButton(
            onClick = { sp.clearReminderTemplate(type.key); text = type.defaultTemplate },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reset to default", style = MaterialTheme.typography.labelSmall)
        }
    }
}
