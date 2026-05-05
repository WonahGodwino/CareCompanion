package com.carecompanion.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .verticalScroll(rememberScrollState())
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

            // ── KPI summary row ────────────────────────────────────────────
            syncState.dashboardSummary?.let { summary ->
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        KpiChip(
                            label = "TX_CURR",
                            value = summary.activeTxCurr.toString(),
                            containerColor = Color(0xFFE8F5E9),
                            contentColor  = Color(0xFF2E7D32),
                        )
                    }
                    item {
                        KpiChip(
                            label = "IIT this week",
                            value = summary.iitThisWeek.toString(),
                            containerColor = Color(0xFFFFF8E1),
                            contentColor  = Color(0xFFF57F17),
                        )
                    }
                    item {
                        KpiChip(
                            label = "Biometric coverage",
                            value = "${"%.1f".format(summary.biometricCoveragePct)}%",
                            containerColor = Color(0xFFE0F7FA),
                            contentColor  = Color(0xFF00838F),
                        )
                    }
                }
            }

            // ── AI Intelligence Banner ──────────────────────────────────
            AiComingSoonBanner()

            Text(
                "Clinical Functions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
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

@Composable
private fun KpiChip(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Coming Soon Banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AiComingSoonBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFF880E4F))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // AI icon badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AI Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD54F))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Phase 1 — Q3 2026",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Predictive IIT risk scoring · Smart defaulter tracing · Viral load forecasting · Clinical decision support",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
            }

            // Coming soon pill on right
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F).copy(alpha = 0.80f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Coming\nSoon",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.70f),
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
