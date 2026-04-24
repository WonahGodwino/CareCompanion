package com.carecompanion.presentation.ui.verify

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.biometric.models.FingerType
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.RecallViewModel
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.presentation.viewmodels.VerifyStep
import com.carecompanion.presentation.viewmodels.VerifyViewModel
import com.carecompanion.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    viewModel: VerifyViewModel = hiltViewModel(),
    patientPickerViewModel: RecallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isScannerBusy = uiState.step == VerifyStep.SCANNING || uiState.step == VerifyStep.MATCHING
    val scannerReadyLabel = when {
        isScannerBusy -> "Busy"
        uiState.isScannerReady -> "Ready"
        else -> "Not Ready"
    }
    val scannerReadyStatus = when {
        isScannerBusy -> ScannerBadgeStatus.Busy
        uiState.isScannerReady -> ScannerBadgeStatus.Positive
        else -> ScannerBadgeStatus.Negative
    }
    val scannerSummaryText = "Connected: ${if (uiState.isScannerConnected) "Yes" else "No"} | " +
        "Access: ${if (uiState.isScannerAccessGranted) "Granted" else "Not granted"} | " +
        "Ready: ${if (isScannerBusy) "Busy" else if (uiState.isScannerReady) "Yes" else "No"}"
    val selectedPatient by sharedViewModel.selectedPatient.collectAsState()
    val patientPickerState by patientPickerViewModel.uiState.collectAsState()
    var showPatientPicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPatient?.uuid) {
        viewModel.reset()
        viewModel.refreshTemplateGroupCountForPatient(selectedPatient)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biometric Verification", fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Info card
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Verification", fontWeight = FontWeight.SemiBold)
                        if (selectedPatient == null) {
                            Text("Select a client to load template groups", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("${uiState.selectedClientTemplateGroups} template groups loaded", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(scannerSummaryText, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusBadge(
                            label = if (uiState.isScannerConnected) "Connected" else "Disconnected",
                            status = if (uiState.isScannerConnected) ScannerBadgeStatus.Positive else ScannerBadgeStatus.Negative
                        )
                        StatusBadge(
                            label = if (uiState.isScannerAccessGranted) "Access Granted" else "Access Needed",
                            status = if (uiState.isScannerAccessGranted) ScannerBadgeStatus.Positive else ScannerBadgeStatus.Negative
                        )
                        StatusBadge(
                            label = scannerReadyLabel,
                            status = scannerReadyStatus
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val currentPatient = selectedPatient
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Client Selection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (currentPatient != null) {
                            TextButton(onClick = { showPatientPicker = true }) {
                                Text("Change Client")
                            }
                        }
                    }

                    if (currentPatient == null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "No client selected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Select a client before scanning to verify fingerprint against the correct person.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Button(
                                    onClick = { showPatientPicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Select Client To Continue", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Text(
                            currentPatient.fullName ?: "${currentPatient.firstName ?: ""} ${currentPatient.surname ?: ""}".trim(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Hospital No: ${currentPatient.hospitalNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Finger selector
            Text("Select Finger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(FingerType.values()) { finger ->
                    FilterChip(
                        selected = uiState.selectedFinger == finger,
                        onClick = { viewModel.selectFinger(finger) },
                        label = { Text(finger.displayName.split(" ").last(), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider()

            // Scan area
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = uiState.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "scan_state") { step ->
                    when (step) {
                        VerifyStep.IDLE -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Place finger on scanner", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            Text(
                                selectedPatient?.let { "Client: ${it.fullName ?: it.hospitalNumber}" } ?: "Select a client to continue",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedPatient == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text("Selected: ${uiState.selectedFinger.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { viewModel.startScan(selectedPatient) },
                                enabled = selectedPatient != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00695C),
                                    contentColor = Color.White,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start Scan", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        VerifyStep.SCANNING -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(64.dp))
                            Text("Scanning fingerprint...", style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(onClick = viewModel::reset) { Text("Cancel") }
                        }
                        VerifyStep.MATCHING -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(64.dp))
                            Text(
                                selectedPatient?.let { "Matching ${uiState.selectedFinger.displayName} against ${it.fullName ?: it.hospitalNumber}..." }
                                    ?: "Matching...",
                                textAlign = TextAlign.Center
                            )
                        }
                        VerifyStep.MATCHED -> {
                            val mp = uiState.matchedPatient
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Match Found!", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Text("Score: ${"%.1f".format(uiState.matchScore)}%", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                                    mp?.patient?.let { p ->
                                        Text(p.fullName ?: "${p.firstName ?: ""} ${p.surname ?: ""}".trim(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        Text("Hospital No: ${p.hospitalNumber}", color = Color.White.copy(alpha = 0.85f))
                                        p.sex?.let { Text("Sex: $it", color = Color.White.copy(alpha = 0.85f)) }
                                        p.dateOfBirth?.let { Text("Age: ${DateUtils.calculateAge(it)} | DOB: ${DateUtils.formatDate(it)}", color = Color.White.copy(alpha = 0.85f)) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2E7D32)),
                                            onClick = {
                                                mp?.patient?.let {
                                                    sharedViewModel.setSelectedPatient(it)
                                                    navController.navigate(Screen.PatientProfile.createRoute(it.uuid))
                                                }
                                            }
                                        ) { Text("View Profile") }
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = viewModel::reset
                                        ) { Text("New Scan", color = Color.White) }
                                    }
                                }
                            }
                        }
                        VerifyStep.NO_MATCH -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                            Text("No Match Found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Text("Score: ${"%.1f".format(uiState.matchScore)}% (threshold: 60%)", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = viewModel::reset) { Text("Try Again") }
                        }
                        VerifyStep.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                            Text(uiState.errorMessage ?: "Scanner error", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                            Text(
                                friendlyScannerHint(
                                    isScannerConnected = uiState.isScannerConnected,
                                    isScannerAccessGranted = uiState.isScannerAccessGranted,
                                    isScannerReady = uiState.isScannerReady
                                ),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = viewModel::retryScanner) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }

    if (showPatientPicker) {
        AlertDialog(
            onDismissRequest = { showPatientPicker = false },
            title = { Text("Select Client") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = patientPickerState.searchQuery,
                        onValueChange = patientPickerViewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name or hospital number") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(patientPickerState.patients, key = { it.uuid }) { patient ->
                            SelectedPatientRow(
                                patient = patient,
                                onClick = {
                                    sharedViewModel.setSelectedPatient(patient)
                                    showPatientPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPatientPicker = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun StatusBadge(label: String, status: ScannerBadgeStatus) {
    val (background, textColor) = when (status) {
        ScannerBadgeStatus.Positive -> Color(0xFF2E7D32) to Color.White
        ScannerBadgeStatus.Negative -> Color(0xFFC62828) to Color.White
        ScannerBadgeStatus.Busy -> Color(0xFFF9A825) to Color.Black
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = background
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private enum class ScannerBadgeStatus { Positive, Negative, Busy }

private fun friendlyScannerHint(
    isScannerConnected: Boolean,
    isScannerAccessGranted: Boolean,
    isScannerReady: Boolean
): String {
    if (!isScannerConnected) return "Scanner not detected. Plug in the USB fingerprint scanner and tap Retry."
    if (!isScannerAccessGranted) return "Scanner detected, but USB access is not granted. Tap Retry and allow USB permission."
    if (!isScannerReady) return "Scanner connected and permitted, but still preparing. Wait a few seconds and try again."
    return "Scanner is ready. Try scanning again."
}

@Composable
private fun SelectedPatientRow(patient: Patient, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                patient.fullName ?: "${patient.firstName ?: ""} ${patient.surname ?: ""}".trim().ifBlank { "Unknown" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Hospital No: ${patient.hospitalNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}