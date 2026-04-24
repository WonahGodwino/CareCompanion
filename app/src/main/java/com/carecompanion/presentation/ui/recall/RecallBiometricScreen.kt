package com.carecompanion.presentation.ui.recall

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.viewmodels.RecallBiometricViewModel
import com.carecompanion.presentation.viewmodels.RecallStep
import com.carecompanion.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallBiometricScreen(
    navController: NavController,
    viewModel: RecallBiometricViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biometric Identification", fontWeight = FontWeight.Bold) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Identification", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${uiState.totalTemplateGroups} template groups loaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(uiState.scannerInfoText, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (uiState.isScannerConnected)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                if (uiState.isScannerConnected) "Connected" else "Disconnected",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (uiState.isScannerAccessGranted)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                if (uiState.isScannerAccessGranted) "Access Granted" else "Access Needed",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (uiState.isScannerReady)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                if (uiState.isScannerReady) "Ready" else "Not Ready",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Main scan area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "recall_state"
                ) { step ->
                    when (step) {
                        RecallStep.IDLE -> IdleContent(
                            isScannerReady = uiState.isScannerReady,
                            onScanClick = viewModel::startScan
                        )
                        RecallStep.SCANNING -> ScanningContent()
                        RecallStep.MATCHING -> MatchingContent()
                        RecallStep.MATCHED -> {
                            val mp = uiState.matchedPatient
                            if (mp != null) {
                                MatchedContent(
                                    hospitalNumber = mp.patient.hospitalNumber,
                                    fullName = mp.patient.fullName
                                        ?: "${mp.patient.firstName ?: ""} ${mp.patient.surname ?: ""}".trim(),
                                    sex = mp.patient.sex,
                                    dateOfBirth = mp.patient.dateOfBirth?.let { DateUtils.formatDate(it) },
                                    matchScore = uiState.matchScore,
                                    onScanAgain = viewModel::reset
                                )
                            }
                        }
                        RecallStep.NO_MATCH -> NoMatchContent(onRetry = viewModel::reset)
                        RecallStep.ERROR -> {} // handled by dialog below
                    }
                }
            }
        }
    }

    // Error dialog
    if (uiState.step == RecallStep.ERROR && uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            icon = { Icon(Icons.Default.ErrorOutline, null) },
            title = { Text("Scanner Error") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiState.errorMessage!!)
                    Text(
                        friendlyScannerHint(
                            isScannerConnected = uiState.isScannerConnected,
                            isScannerAccessGranted = uiState.isScannerAccessGranted,
                            isScannerReady = uiState.isScannerReady
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::retryScanner) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { navController.popBackStack() }) { Text("Cancel") }
            }
        )
    }
}

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
private fun IdleContent(isScannerReady: Boolean, onScanClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Place any finger on the scanner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            "The system will automatically identify the client",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onScanClick,
            enabled = isScannerReady,
            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
        ) {
            Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Scan", style = MaterialTheme.typography.titleMedium)
        }
        if (!isScannerReady) {
            Text(
                "Connect a USB fingerprint scanner to proceed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanningContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(80.dp), strokeWidth = 6.dp)
        Text(
            "Scanning...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Keep your finger still on the scanner",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MatchingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(80.dp), strokeWidth = 6.dp)
        Text(
            "Matching...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Searching database for a match",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatchedContent(
    hospitalNumber: String,
    fullName: String,
    sex: String?,
    dateOfBirth: String?,
    matchScore: Double,
    onScanAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Client Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(label = "Hospital Number", value = hospitalNumber)
                HorizontalDivider()
                InfoRow(label = "Name", value = fullName.ifBlank { "N/A" })
                HorizontalDivider()
                InfoRow(label = "Sex", value = sex ?: "N/A")
                HorizontalDivider()
                InfoRow(label = "Date of Birth", value = dateOfBirth ?: "N/A")
                HorizontalDivider()
                InfoRow(
                    label = "Match Score",
                    value = String.format("%.1f%%", matchScore)
                )
            }
        }

        Button(
            onClick = onScanAgain,
            modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan Again")
        }
    }
}

@Composable
private fun NoMatchContent(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            Icons.Default.PersonOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            "No Match Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            "No client matched this fingerprint.\nEnsure data is synced and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}
