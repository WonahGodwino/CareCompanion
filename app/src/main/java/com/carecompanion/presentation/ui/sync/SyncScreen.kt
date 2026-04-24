package com.carecompanion.presentation.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.presentation.viewmodels.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val intervals = listOf(5, 15, 30, 60, 120, 360)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Sync", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Status card
            val syncCardColor = when (uiState.isSuccess) {
                true  -> Color(0xFF2E7D32)   // solid green
                false -> Color(0xFFC62828)   // solid red
                null  -> MaterialTheme.colorScheme.surfaceVariant
            }
            val syncContentColor = when (uiState.isSuccess) {
                null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> Color.White
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = syncCardColor)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (uiState.isSuccess) { true -> Icons.Default.CheckCircle; false -> Icons.Default.Error; null -> Icons.Default.CloudSync },
                            null, modifier = Modifier.size(24.dp), tint = syncContentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (uiState.isSyncing) "Sync in Progress" else "Sync Status",
                            fontWeight = FontWeight.SemiBold, color = syncContentColor
                        )
                    }

                    // Live progress bar while syncing
                    if (uiState.isSyncing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }

                    // Live status message (e.g. "Syncing patients... 500 / 4800")
                    if (uiState.statusMessage.isNotEmpty())
                        Text(uiState.statusMessage, style = MaterialTheme.typography.bodySmall, color = syncContentColor)

                    if (!uiState.isSyncing)
                        Text("Last sync: ${uiState.lastSyncDate}", style = MaterialTheme.typography.bodySmall, color = syncContentColor)

                    if (uiState.isSuccess == true)
                        Text("Patients: ${uiState.patientsAdded} | Biometrics: ${uiState.biometricsAdded}",
                            style = MaterialTheme.typography.bodySmall, color = syncContentColor)
                }
            }

            // Manual sync button
            Button(
                onClick = viewModel::syncNow,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSyncing
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(Icons.Default.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }

            HorizontalDivider()

            // Auto-sync toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Auto Sync", fontWeight = FontWeight.Medium)
                    Text("Sync automatically in background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = uiState.autoSyncEnabled, onCheckedChange = viewModel::setAutoSync)
            }

            // Interval selector
            AnimatedVisibility(visible = uiState.autoSyncEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sync Interval", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        intervals.forEach { min ->
                            FilterChip(
                                selected = uiState.syncIntervalMinutes == min,
                                onClick = { viewModel.setSyncInterval(min) },
                                label = { Text(if (min < 60) "${min}m" else "${min/60}h") }
                            )
                        }
                    }
                }
            }
        }
    }
}