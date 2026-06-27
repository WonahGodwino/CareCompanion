package com.carecompanion.presentation.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.PatientProfileViewModel
import com.carecompanion.presentation.viewmodels.ViralLoadCurrentUiState
import com.carecompanion.presentation.viewmodels.ViralLoadHistoryUiItem
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.presentation.viewmodels.VisitEventType
import com.carecompanion.presentation.viewmodels.VisitTimelineEntry
import com.carecompanion.utils.DateUtils
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientScreen(
    navController: NavController,
    patientId: String,
    sharedViewModel: SharedViewModel,
    viewModel: PatientProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val captureState by viewModel.captureState.collectAsState()

    LaunchedEffect(patientId) { 
        viewModel.loadPatient(patientId) 
    }

    // Biometric capture dialog
    if (captureState) {
        AlertDialog(
            onDismissRequest = { /* viewModel.resetBiometricCapture() */ },
            title = { Text("Capture Biometric") },
            text = { Text("Simulate biometric capture for demonstration. Press 'Capture' to proceed.") },
            confirmButton = {
                Button(onClick = {
                    // TODO: Replace with real capture logic
                    val fingerType = com.carecompanion.biometric.models.FingerType.RIGHT_THUMB
                    val template = ByteArray(256) { 1 } // Mocked template
                    val templateType = "ISO"
                    viewModel.onBiometricCaptured(fingerType, template, templateType)
                }) {
                    Text("Capture")
                }
            },
            dismissButton = {
                Button(onClick = { /* viewModel.resetBiometricCapture() */ }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        uiState.patient?.let { it.fullName ?: "${it.firstName} ${it.surname}" } ?: "Patient Profile", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = { 
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.Default.ArrowBack, "Back") 
                    } 
                },
                actions = {
                    IconButton(onClick = {
                        uiState.patient?.let(sharedViewModel::setSelectedPatient)
                        navController.navigate(Screen.Verify.route)
                    }) {
                        Icon(Icons.Default.Fingerprint, "Verify")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator() 
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Patient Demographics Card
                item {
                    PatientDemographicsCard(uiState.patient)
                }

                // AHD Alert Banner
                if (uiState.isAhd) {
                    item { AhdAlertBanner() }
                }

                // Current Regimen Card
                uiState.currentRegimenShortName?.let { regimenShortName ->
                    item {
                        CurrentRegimenCard(
                            shortName = regimenShortName,
                            fullName  = uiState.currentRegimenFullName,
                            line      = uiState.currentRegimenLine
                        )
                    }
                }

                // Biometric Enrollment Card
                item {
                    BiometricEnrollmentCard(
                        hasBiometric    = uiState.hasBiometric,
                        biometricCount  = uiState.biometricCount,
                        recaptureBreakdown = uiState.recaptureBreakdown,
                        recaptureOverdue   = uiState.recaptureOverdue,
                        recaptureRecommended = uiState.recaptureRecommended,
                        daysSinceLastBiometric = uiState.daysSinceLastBiometric,
                        onCapture = { viewModel.startBiometricCapture() }
                    )
                }

                // Viral Load Current Card
                item {
                    ViralLoadCurrentCard(uiState.currentViralLoad)
                }

                // Viral Load History Card
                item {
                    ViralLoadHistoryCard(uiState.viralLoadHistory)
                }

                // Service Eligibility Section
                item {
                    ServiceEligibilitySection(uiState.artPharmacy, uiState.currentViralLoad)
                }

                // Visit Timeline
                if (uiState.visitTimeline.isNotEmpty()) {
                    item { VisitTimelineCard(uiState.visitTimeline) }
                }

                // ART Pharmacy History
                if (uiState.artPharmacy.isNotEmpty()) {
                    item {
                        Text("ART Pharmacy Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(uiState.artPharmacy) { record ->
                        ArtPharmacyCard(record)
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(), 
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp), 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MedicalServices, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text("No ART pharmacy records", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientDemographicsCard(patient: Patient?) {
    if (patient == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with avatar and name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (patient.firstName?.firstOrNull() ?: patient.fullName?.firstOrNull() ?: '?').toString().uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        patient.fullName ?: "${patient.firstName ?: ""} ${patient.surname ?: ""}".trim(),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            patient.hospitalNumber,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))

            // Demographics grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRowWithIcon(
                    if (patient.sex == "F") "Female" else if (patient.sex == "M") "Male" else "Unknown",
                    Icons.Default.Person
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoRowWithIcon(
                        patient.dateOfBirth?.let { DateUtils.formatDate(it) } ?: "Unknown",
                        Icons.Default.CalendarToday
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoRowWithIcon(
                        "${DateUtils.calculateAge(patient.dateOfBirth)} years${if (patient.isDateOfBirthEstimated) " (est.)" else ""}",
                        Icons.Default.Cake
                    )
                }
                patient.ninNumber?.let { InfoRowWithIcon(it, Icons.Default.Badge) }
                patient.phoneNumber?.let { InfoRowWithIcon(it, Icons.Default.Phone) }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))

                // NDR Match Status — dedicated row with clear colour coding so clinicians
                // can instantly see whether this patient exists in the national registry.
                NdrMatchStatusRow(patient.ndrMatchedStatus)
            }
        }
    }
}

// Normalises all known WINCO / NPHCDA NDR match_outcome values to a boolean.
// WINCO currently sends "Matched" / "Not Matched" but some NDR configurations
// emit "YES", "MATCH", "NDR_MATCHED" etc. — all are treated as matched here.
private fun isNdrMatched(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val v = raw.trim().uppercase().replace("_", "").replace("-", "").replace(" ", "")
    return v == "MATCHED" || v == "MATCH" || v == "YES" || v == "Y" ||
        v == "NDRMATCHED" || v == "TRUE" || v == "1"
}

@Composable
private fun NdrMatchStatusRow(ndrMatchedStatus: String?) {
    val isMatched = isNdrMatched(ndrMatchedStatus)
    val containerColor = if (isMatched) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
    val contentColor  = if (isMatched) Color(0xFF1B5E20) else Color(0xFF795548)
    val icon          = if (isMatched) Icons.Default.VerifiedUser else Icons.Default.Warning
    val label         = if (isMatched) "NDR Matched" else "Not Matched on NDR"
    val sublabel      = if (isMatched)
        "Patient record confirmed in National Data Repository"
    else
        "Patient not yet matched in National Data Repository"

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, color = contentColor, style = MaterialTheme.typography.bodyMedium)
                Text(sublabel, color = contentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoRowWithIcon(value: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BiometricEnrollmentCard(
    hasBiometric: Boolean,
    biometricCount: Int,
    recaptureBreakdown: Map<Int, Int>,
    recaptureOverdue: Boolean,
    recaptureRecommended: Boolean,
    daysSinceLastBiometric: Long?,
    onCapture: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Biometric Enrollment", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Button(onClick = onCapture) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Capture")
                }
            }

            // Recapture overdue — urgent compliance banner (≥ 24 months + not NDR matched)
            if (recaptureOverdue) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Biometric Recapture Overdue",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Last biometric captured ${daysSinceLastBiometric ?: "—"} days ago. " +
                                    "Patient is not matched on NDR. Recapture required for PEPFAR compliance.",
                                color = Color(0xFFB71C1C),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else if (recaptureRecommended) {
                // Routine recommendation — 15-day programme cadence
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFF57F17), modifier = Modifier.size(20.dp))
                        Text(
                            "Recapture recommended — ${daysSinceLastBiometric ?: "—"} days since last capture (target: ≤ 15 days).",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                if (hasBiometric) "Client has biometric" else "Client has no biometric",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (hasBiometric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                "Biometric records: $biometricCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RecaptureStatusSection(recaptureBreakdown)
        }
    }
}

@Composable
private fun RecaptureStatusSection(recaptureBreakdown: Map<Int, Int>) {
    val hasBasePrint = (recaptureBreakdown[0] ?: 0) > 0
    val hasFirstRecapture = (recaptureBreakdown[1] ?: 0) > 0
    val additionalRecaptures = recaptureBreakdown.filterKeys { it > 1 }.toSortedMap()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecaptureStatusChip(
            modifier = Modifier.weight(1f),
            label = "R0 Base Print",
            count = recaptureBreakdown[0] ?: 0,
            available = hasBasePrint
        )
        RecaptureStatusChip(
            modifier = Modifier.weight(1f),
            label = "R1 Recapture",
            count = recaptureBreakdown[1] ?: 0,
            available = hasFirstRecapture
        )
    }

    if (additionalRecaptures.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("Other recaptures", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            additionalRecaptures.forEach { (recapture, count) ->
                SuggestionChip(onClick = {}, enabled = false, label = { Text("R$recapture ($count)") })
            }
        }
    }
}

@Composable
private fun RecaptureStatusChip(modifier: Modifier = Modifier, label: String, count: Int, available: Boolean) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (available) Color(0xFFE7F6EC) else MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (available) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold)
            Text(if (available) "Available ($count)" else "Not available",
                style = MaterialTheme.typography.bodySmall,
                color = if (available) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun ViralLoadCurrentCard(current: ViralLoadCurrentUiState) {
    val palette = viralLoadPalette(current.resultLabel, current.statusLabel)

    val badgeIcon = when (current.resultLabel) {
        "Unsuppressed" -> Icons.Default.Warning
        "Undetected" -> Icons.Default.CheckCircle
        "Suppressed" -> Icons.Default.VerifiedUser
        else -> Icons.Default.Science
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = palette.onContainer.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Biotech, null, tint = palette.onContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        current.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.onContainer
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = palette.onContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(badgeIcon, null, tint = palette.onContainer, modifier = Modifier.size(16.dp))
                        Text(
                            current.statusLabel.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.onContainer
                        )
                    }
                }

                Text(
                    current.resultValueLabel,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.onContainer
                )

                if (current.resultLabel.isNotBlank()) {
                    Text(
                        current.resultLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onContainer.copy(alpha = 0.80f)
                    )
                }

                HorizontalDivider(color = palette.onContainer.copy(alpha = 0.15f))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CalendarToday, null, tint = palette.onContainer.copy(alpha = 0.70f), modifier = Modifier.size(14.dp))
                        Text(current.sampleDateLabel, style = MaterialTheme.typography.bodySmall, color = palette.onContainer.copy(alpha = 0.85f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Event, null, tint = palette.onContainer.copy(alpha = 0.70f), modifier = Modifier.size(14.dp))
                        Text(current.resultDateLabel, style = MaterialTheme.typography.bodySmall, color = palette.onContainer.copy(alpha = 0.85f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = palette.onContainer.copy(alpha = 0.70f), modifier = Modifier.size(14.dp))
                        Text(current.nextDueLabel, style = MaterialTheme.typography.bodySmall, color = palette.onContainer.copy(alpha = 0.85f))
                    }
                }

                current.flagLabel?.let { flag ->
                    Surface(
                        color = palette.flagContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = palette.onFlagContainer, modifier = Modifier.size(14.dp))
                            Text(
                                flag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.onFlagContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViralLoadHistoryCard(history: List<ViralLoadHistoryUiItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Viral Load History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            if (history.isEmpty()) {
                Text(
                    "No viral load history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            } else {
                history.forEachIndexed { index, item ->
                    ViralLoadHistoryRow(item)
                    if (index < history.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViralLoadHistoryRow(item: ViralLoadHistoryUiItem) {
    val palette = viralLoadPalette(item.resultStatus, item.resultFlag)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.container,
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header line with date and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (item.isPending) "Viral Load Sample Collection" else "Viral Load Date",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onContainer
                )
                if (item.isPending || item.isOverdueNoResult) {
                    Surface(
                        color = palette.flagContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            item.resultFlag,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onFlagContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Date rows
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    null,
                    tint = palette.onContainer.copy(alpha = 0.70f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Sample Date: ${item.dateSampleCollected?.let { DateUtils.formatDate(it) } ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onContainer.copy(alpha = 0.85f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Event,
                    null,
                    tint = palette.onContainer.copy(alpha = 0.70f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Result Date: ${item.dateResultReported?.let { DateUtils.formatDate(it) } ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onContainer.copy(alpha = 0.85f)
                )
            }

            // VL value (if not pending)
            if (!item.isPending && item.resultValueLabel.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Science,
                        null,
                        tint = palette.onContainer.copy(alpha = 0.70f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "VL: ${item.resultValueLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Status badge (if not pending)
            if (!item.isPending && item.resultStatus.isNotBlank()) {
                Surface(
                    color = palette.onContainer.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        item.resultStatus,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtPharmacyCard(record: ArtPharmacy) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(DateUtils.formatDate(record.visitDate), fontWeight = FontWeight.SemiBold)
                record.nextAppointment?.let { 
                    Text("Next: ${DateUtils.formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) 
                }
            }
            record.mmdType?.let { Text("MMD Type: $it", style = MaterialTheme.typography.bodySmall) }
            record.dsdModel?.let { Text("DSD Model: $it", style = MaterialTheme.typography.bodySmall) }
            record.refillPeriod?.let { Text("Refill: ${it}m supply", style = MaterialTheme.typography.bodySmall) }
            record.adherence?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (it) Icons.Default.CheckCircle else Icons.Default.Cancel, null, modifier = Modifier.size(14.dp), tint = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Adherence: ${if (it) "Good" else "Poor"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ServiceEligibilitySection(
    artRecords: List<ArtPharmacy>,
    currentViralLoad: ViralLoadCurrentUiState
) {
    val latest = artRecords.maxByOrNull { it.visitDate.time }
    val daysOverdue = latest?.nextAppointment?.let {
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it.time).toInt().coerceAtLeast(0)
    } ?: 0
    val daysUntilNextAppt = latest?.nextAppointment?.let {
        TimeUnit.MILLISECONDS.toDays(it.time - System.currentTimeMillis()).toInt()
    }

    val iitEligible = latest?.nextAppointment != null && daysOverdue >= 28
    val missedApptEligible = latest?.nextAppointment != null && daysOverdue >= 1
    val artRefillEligible = latest?.nextAppointment != null && (daysUntilNextAppt ?: Int.MAX_VALUE) <= 7

    val vlPending = currentViralLoad.isPendingResult
    val vlOverduePending = currentViralLoad.isOverduePendingResult
    val vlEligible = currentViralLoad.eligibility?.isEligibleToday == true && !vlPending

    val items = listOf(
        ServiceEligibilityItem("IIT", iitEligible, if (iitEligible) "Eligible: missed refill >= 28 days" else "Not eligible"),
        ServiceEligibilityItem("ART Refills", artRefillEligible, when {
            latest?.nextAppointment == null -> "No next appointment"
            artRefillEligible -> "Eligible: appointment is within 7 days"
            else -> "Not eligible"
        }),
        ServiceEligibilityItem("Missed Appointments", missedApptEligible, if (missedApptEligible) "Eligible: appointment date has passed" else "Not eligible"),
        ServiceEligibilityItem("Viral Load", vlEligible, when {
            vlOverduePending -> "Over due pending result (>30 days since sample collection)"
            vlPending -> "Pending Viral Load Result (sample collected, awaiting result)"
            vlEligible -> "Eligible based on sample collection due date"
            else -> "Not eligible by sample collection due date"
        }),
        ServiceEligibilityItem("TPT", false, "Not eligible"),
        ServiceEligibilityItem("TB", false, "Not eligible"),
        ServiceEligibilityItem("AHD", false, "Not eligible")
    )
    
    val eligibleCount = items.count { it.eligible }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Service Eligibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$eligibleCount of ${items.size} services currently eligible for this client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (item.eligible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                if (item.eligible) "Eligible" else "Not Eligible",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.eligible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (idx < items.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AhdAlertBanner() {
    Surface(
        color = Color(0xFFFBE9E7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Warning, contentDescription = null,
                tint = Color(0xFFD84315), modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Advanced HIV Disease (AHD)", fontWeight = FontWeight.Bold,
                    color = Color(0xFFBF360C), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Latest VL < 200 copies/mL. WHO criteria for AHD in adults on ART. " +
                    "Assess for opportunistic infections, CD4 count, and intensified management.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFBF360C),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

@Composable
private fun CurrentRegimenCard(shortName: String, fullName: String?, line: String?) {
    val lineColor = when (line) {
        "1st Line"    -> Color(0xFF1565C0)
        "2nd Line"    -> Color(0xFFE65100)
        "3rd Line"    -> Color(0xFFC62828)
        "Paediatric"  -> Color(0xFF558B2F)
        else          -> Color(0xFF37474F)
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Medication, contentDescription = null,
                tint = lineColor, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Current Regimen", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(shortName, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = lineColor)
                fullName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            line?.let {
                Surface(
                    color = lineColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold, color = lineColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun VisitTimelineCard(events: List<VisitTimelineEntry>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Visit Timeline", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Last ${events.size} events", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            events.forEachIndexed { idx, event ->
                VisitTimelineRow(event = event, isLast = idx == events.lastIndex)
            }
        }
    }
}

@Composable
private fun VisitTimelineRow(event: VisitTimelineEntry, isLast: Boolean) {
    val (color, icon) = when (event.type) {
        VisitEventType.ART_REFILL    -> Color(0xFF1565C0) to Icons.Default.MedicalServices
        VisitEventType.VL_SAMPLE     -> Color(0xFF6A1B9A) to Icons.Default.Biotech
        VisitEventType.VL_RESULT     -> Color(0xFF2E7D32) to Icons.Default.Science
        VisitEventType.APPOINTMENT   -> Color(0xFF00695C) to Icons.Default.Event
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline rail
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            if (!isLast) Box(modifier = Modifier.width(2.dp).height(32.dp).background(color.copy(alpha = 0.20f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Text(event.type.label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = color)
                Spacer(Modifier.weight(1f))
                Text(DateUtils.formatDate(event.date), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(event.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            event.regimenName?.let { rn ->
                Text("Regimen: $rn", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

private data class ServiceEligibilityItem(
    val name: String,
    val eligible: Boolean,
    val note: String
)

private data class ViralLoadPalette(
    val container: Color,
    val onContainer: Color,
    val flagContainer: Color,
    val onFlagContainer: Color,
)

private fun viralLoadPalette(resultLabel: String, statusLabel: String): ViralLoadPalette {
    val label = resultLabel.trim().uppercase()
    val status = statusLabel.trim().uppercase()
    return when {
        status.contains("PENDING") || status.contains("COLLECTION") -> ViralLoadPalette(
            container = Color(0xFFF5F5F5),
            onContainer = Color(0xFF263238),
            flagContainer = Color(0xFFE0E0E0),
            onFlagContainer = Color(0xFF263238),
        )
        label.contains("UNDETECTED") -> ViralLoadPalette(
            container = Color(0xFFE8F5E9),
            onContainer = Color(0xFF1B5E20),
            flagContainer = Color(0xFFC8E6C9),
            onFlagContainer = Color(0xFF1B5E20),
        )
        label.contains("SUPPRESSED") -> ViralLoadPalette(
            container = Color(0xFFFFFDE7),
            onContainer = Color(0xFF7A5A00),
            flagContainer = Color(0xFFFFF9C4),
            onFlagContainer = Color(0xFF6D4C00),
        )
        label.contains("UNSUPPRESSED") || status.contains("OVERDUE") -> ViralLoadPalette(
            container = Color(0xFFFFEBEE),
            onContainer = Color(0xFFB71C1C),
            flagContainer = Color(0xFFFFCDD2),
            onFlagContainer = Color(0xFFB71C1C),
        )
        else -> ViralLoadPalette(
            container = Color(0xFFF2F4F7),
            onContainer = Color(0xFF263238),
            flagContainer = Color(0xFFE9EEF5),
            onFlagContainer = Color(0xFF263238),
        )
    }
}