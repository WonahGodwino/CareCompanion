package com.carecompanion.presentation.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.presentation.viewmodels.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val syncState by syncViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CareCompanion",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "HIV Patient Management System",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor          = MaterialTheme.colorScheme.primary,
                    titleContentColor       = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor  = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Sync.route) }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Sync status banner ─────────────────────────────────────────
            val bannerColor = when (syncState.isSuccess) {
                true  -> Color(0xFF2E7D32)   // solid green
                false -> Color(0xFFC62828)   // solid red
                null  -> MaterialTheme.colorScheme.surfaceVariant
            }
            val bannerContent = when (syncState.isSuccess) {
                null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> Color.White
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = bannerColor),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (syncState.isSuccess) {
                                    true  -> Icons.Default.CheckCircle
                                    false -> Icons.Default.Error
                                    null  -> Icons.Default.CloudSync
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = bannerContent
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (syncState.isSyncing) "Syncing…" else "Last Sync: ${syncState.lastSyncDate}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = bannerContent
                            )
                        }
                        if (!syncState.isSyncing) {
                            TextButton(
                                onClick = syncViewModel::syncNow,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = bannerContent)
                                Spacer(Modifier.width(4.dp))
                                Text("Sync Now", style = MaterialTheme.typography.labelSmall, color = bannerContent)
                            }
                        }
                    }

                    // Progress bar while syncing
                    if (syncState.isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        if (syncState.statusMessage.isNotEmpty()) {
                            Text(syncState.statusMessage, style = MaterialTheme.typography.bodySmall,
                                color = bannerContent)
                        }
                    }

                    // Result summary
                    if (syncState.isSuccess == true) {
                        Text(
                            "${syncState.patientsAdded} patients · ${syncState.biometricsAdded} biometrics updated",
                            style = MaterialTheme.typography.bodySmall,
                            color = bannerContent
                        )
                    } else if (syncState.isSuccess == false && syncState.statusMessage.isNotEmpty()) {
                        Text(syncState.statusMessage, style = MaterialTheme.typography.bodySmall,
                            color = bannerContent)
                    }
                }
            }

            Text(
                "Clinical Functions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Services card ──────────────────────────────────────────────
            MenuCard(
                title = "Services",
                description = "ART Refills, Missed Appointments, Viral Load, TPT and AHD tracking",
                icon = Icons.Default.MedicalServices,
                containerColor = Color(0xFFFFF3E0),
                contentColor = Color(0xFFE65100),
                onClick = { navController.navigate(Screen.Services.route) }
            )

            // ── Patient card ──────────────────────────────────────────────
            MenuCard(
                title = "Patient",
                description = "Browse and search the full list of active HIV/ART clients",
                icon = Icons.Default.PeopleAlt,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { navController.navigate(Screen.PatientList.route) }
            )

            // ── Recall card ───────────────────────────────────────────────
            MenuCard(
                title = "Client Identification",
                description = "Identify HIV/ART clients using biometric fingerprint scanning",
                icon = Icons.Default.Fingerprint,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { navController.navigate(Screen.RecallBiometric.route) }
            )

            // ── Client Verification card ────────────────────────────────────
            MenuCard(
                title = "Client Verification",
                description = "Verify client identity using biometric fingerprint scanning",
                icon = Icons.Default.VerifiedUser,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    sharedViewModel.clearSelectedPatient()
                    navController.navigate(Screen.Verify.route)
                }
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = contentColor
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
