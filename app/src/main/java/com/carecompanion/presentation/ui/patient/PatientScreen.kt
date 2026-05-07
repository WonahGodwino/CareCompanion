package com.carecompanion.presentation.ui.patient

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.presentation.viewmodels.ServiceEligibilityUI
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.PatientProfileViewModel
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.utils.DateUtils
import java.util.Date
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

    LaunchedEffect(patientId) { viewModel.loadPatient(patientId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.patient?.let { it.fullName ?: "${it.firstName} ${it.surname}" } ?: "Patient Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor     = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
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
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                item { PatientDemographicsCard(uiState.patient) }

                item {
                    val biometricStatus = remember(uiState.patient, uiState.biometrics) {
                        calculateBiometricRecaptureStatus(uiState.patient, uiState.biometrics)
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Biometric Enrollment", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                biometricStatus.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = biometricStatus.color
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                biometricStatus.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Biometric records: ${uiState.biometricCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            RecaptureStatusSection(uiState.recaptureBreakdown)
                        }
                    }
                }

                item { ViralLoadSummaryCard(uiState.patient) }

                item { ViralLoadHistoryCard(uiState.viralLoadHistory) }

                item {
                    ServiceEligibilitySection(
                        serviceEligibility = uiState.serviceEligibility,
                        eligibleCount = uiState.eligibleCount,
                        totalCount = 4,
                        isLoading = uiState.isServiceEligibilityLoading,
                        errorMessage = uiState.serviceEligibilityError
                    )
                }

                if (uiState.artPharmacy.isNotEmpty()) {
                    item {
                        Text("ART Pharmacy History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(uiState.artPharmacy) { record ->
                        ArtPharmacyCard(record)
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(56.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (patient.firstName?.firstOrNull() ?: patient.fullName?.firstOrNull() ?: '?').toString().uppercase(),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(patient.fullName ?: "${patient.firstName ?: ""} ${patient.surname ?: ""}".trim(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(patient.hospitalNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            InfoRow("Sex", patient.sex ?: "Unknown")
            InfoRow("Date of Birth", patient.dateOfBirth?.let { DateUtils.formatDate(it) } ?: "Unknown")
            InfoRow("Age", "${DateUtils.calculateAge(patient.dateOfBirth)} years${if (patient.isDateOfBirthEstimated) " (estimated)" else ""}")
            NdrStatusRow(patient.ndrMatchedStatus)
            patient.ninNumber?.let { InfoRow("NIN", it) }
            patient.phoneNumber?.let { InfoRow("Phone", it) }
        }
    }
}

private data class BiometricRecaptureStatus(
    val title: String,
    val detail: String,
    val color: Color,
)

private fun calculateBiometricRecaptureStatus(patient: Patient?, biometrics: List<Biometric>): BiometricRecaptureStatus {
    if (patient == null) {
        return BiometricRecaptureStatus(
            title = "Biometric status unavailable",
            detail = "Patient context is missing",
            color = Color(0xFF455A64)
        )
    }

    val ageYears = DateUtils.calculateAge(patient.dateOfBirth)
    val hasBasePrint = biometrics.any { (it.recapture) == 0 }
    if (!hasBasePrint && ageYears > 5) {
        return BiometricRecaptureStatus(
            title = "No base print (R0)",
            detail = "Client is older than 5 years and requires base print enrollment",
            color = Color(0xFFB71C1C)
        )
    }

    if (biometrics.isEmpty()) {
        return BiometricRecaptureStatus(
            title = "No biometric captured",
            detail = "Capture biometric to enable recapture scheduling",
            color = Color(0xFFB71C1C)
        )
    }

    val ndrStatus = patient.ndrMatchedStatus?.trim()
    val isMatched = ndrStatus.equals("Match", ignoreCase = true)
    if (isMatched) {
        return BiometricRecaptureStatus(
            title = "Matched in NDR",
            detail = "No biometric recapture is due",
            color = Color(0xFF1B5E20)
        )
    }

    val lastRecaptureDate = biometrics
        .filter { (it.recapture) > 0 }
        .mapNotNull { it.enrollmentDate ?: it.replaceDate }
        .maxByOrNull { it.time }
        ?: biometrics.mapNotNull { it.enrollmentDate ?: it.replaceDate }.maxByOrNull { it.time }

    if (lastRecaptureDate == null) {
        return BiometricRecaptureStatus(
            title = "Recapture due",
            detail = "Capture date missing; client is not NDR matched",
            color = Color(0xFF7A5A00)
        )
    }

    val nextDueDate = Date(lastRecaptureDate.time + TimeUnit.DAYS.toMillis(15))
    val daysUntilDue = TimeUnit.MILLISECONDS.toDays(nextDueDate.time - Date().time)
    return if (daysUntilDue <= 0) {
        BiometricRecaptureStatus(
            title = "Eligible for recapture",
            detail = "Due since ${DateUtils.formatDate(nextDueDate)} (${kotlin.math.abs(daysUntilDue)} day(s) overdue)",
            color = Color(0xFFB71C1C)
        )
    } else {
        BiometricRecaptureStatus(
            title = "Recapture not yet due",
            detail = "Next eligible date: ${DateUtils.formatDate(nextDueDate)} ($daysUntilDue day(s) remaining)",
            color = Color(0xFF7A5A00)
        )
    }
}

private fun getViralLoadColor(resultNumeric: Long?): Color {
    return when {
        resultNumeric == null -> Color(0xFF455A64)
        resultNumeric <= 20 -> Color(0xFF1B5E20)      // Dark green for undetectable
        resultNumeric < 1000 -> Color(0xFF7A5A00)     // Dark amber for suppressed
        else -> Color(0xFFB71C1C)                     // Dark red for unsuppressed
    }
}

private fun getViralLoadBackgroundColor(resultNumeric: Long?): Color {
    return when {
        resultNumeric == null -> Color(0xFFECEFF1)
        resultNumeric <= 20 -> Color(0xFFE8F5E9)
        resultNumeric < 1000 -> Color(0xFFFFF3CD)
        else -> Color(0xFFFFEBEE)                     // Light red
    }
}

private fun getViralLoadLabelColor(resultNumeric: Long?): Color {
    return when {
        resultNumeric == null -> Color(0xFF263238)
        resultNumeric <= 20 -> Color(0xFFFFFFFF)
        resultNumeric < 1000 -> Color(0xFFFFFFFF)
        else -> Color(0xFFFFFFFF)
    }
}

@Composable
private fun ViralLoadSummaryCard(patient: Patient?) {
    if (patient == null) return

    val vlDate = patient.lastViralLoadDate
    val vlNumeric = patient.lastViralLoadResult
    val vlRaw = patient.lastViralLoadResultRaw?.trim()?.takeIf { it.isNotEmpty() }

    val resultLabel = when {
        vlRaw != null -> vlRaw
        vlNumeric != null -> "$vlNumeric cp/mL"
        else -> "No viral load result synced"
    }

    val interpretation = when {
        vlNumeric == null -> "Pending"
        vlNumeric <= 20 -> "Undetectable"
        vlNumeric < 1000 -> "Suppressed"
        else -> "Unsuppressed"
    }

    val statusColor = getViralLoadColor(vlNumeric)
    val backgroundColor = getViralLoadBackgroundColor(vlNumeric)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Viral Load", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = backgroundColor,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Latest Result", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = statusColor)
                            Text(resultLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = statusColor)
                        }
                        Surface(shape = MaterialTheme.shapes.small, color = statusColor) {
                            Text(
                                interpretation,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = getViralLoadLabelColor(vlNumeric)
                            )
                        }
                    }
                    Text(
                        "VL Date: ${vlDate?.let { DateUtils.formatDate(it) } ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ViralLoadHistoryCard(history: List<ViralLoadHistory>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Viral Load History", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            if (history.isEmpty()) {
                Text(
                    "No viral load history available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                return@Column
            }

            history.take(10).forEach { item ->
                val resultLabel = item.resultRaw?.trim()?.takeIf { it.isNotEmpty() }
                    ?: item.resultNumeric?.let { "$it cp/mL" }
                    ?: "N/A"
                val resultDate = item.resultDate ?: item.assayedDate ?: item.sampleDate
                val dateLabel = resultDate?.let { DateUtils.formatDate(it) } ?: "N/A"
                val interpretation = when {
                    item.resultNumeric == null -> "Pending"
                    item.resultNumeric <= 20 -> "Undetectable"
                    item.resultNumeric < 1000 -> "Suppressed"
                    else -> "Unsuppressed"
                }

                val statusColor = getViralLoadColor(item.resultNumeric)
                val backgroundColor = getViralLoadBackgroundColor(item.resultNumeric)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = backgroundColor,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                            Text(
                                resultLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = statusColor) {
                            Text(
                                interpretation,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = getViralLoadLabelColor(item.resultNumeric),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NdrStatusRow(rawStatus: String?) {
    val normalized = rawStatus?.trim().orEmpty()
    val label = if (normalized.isBlank()) "Not available" else normalized
    val (chipBg, chipText) = when {
        normalized.equals("match", ignoreCase = true) -> Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20))
        normalized.equals("not match", ignoreCase = true)
            || normalized.equals("no match", ignoreCase = true)
            || normalized.equals("unmatched", ignoreCase = true) -> Pair(Color(0xFFFFF3E0), Color(0xFF7A5A00))
        normalized.isBlank() -> Pair(Color(0xFFF1F3F4), Color(0xFF455A64))
        else -> Pair(Color(0xFFE3F2FD), Color(0xFF0D47A1))
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("NDR Match Status", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Surface(
            color = chipBg,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = chipText,
            )
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
        Spacer(Modifier.height(10.dp))
        Text(
            "Other recaptures",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            additionalRecaptures.forEach { (recapture, count) ->
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("R$recapture ($count)") }
                )
            }
        }
    }
}

@Composable
private fun RecaptureStatusChip(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    available: Boolean
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (available) Color(0xFFE7F6EC) else MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (available) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (available) "Available ($count)" else "Not available",
                style = MaterialTheme.typography.bodySmall,
                color = if (available) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ArtPharmacyCard(record: ArtPharmacy) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(DateUtils.formatDate(record.visitDate), fontWeight = FontWeight.SemiBold)
                record.nextAppointment?.let { Text("Next: ${DateUtils.formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
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
    serviceEligibility: Map<String, ServiceEligibilityUI>,
    eligibleCount: Int,
    totalCount: Int,
    isLoading: Boolean,
    errorMessage: String?
) {
    // MISSED_APPOINTMENT is collapsed into ART_REFILL — not a separate card
    val serviceOrder = listOf("ART_REFILL", "VIRAL_LOAD", "TPT", "TB_AHD")
    val displayNames = mapOf(
        "ART_REFILL" to "ART Refill & Appointment",
        "VIRAL_LOAD" to "Viral Load",
        "TPT" to "TPT",
        "TB_AHD" to "TB/AHD"
    )

    // Function to get urgency color
    fun getUrgencyColor(urgency: String?, eligible: Boolean): Color {
        if (!eligible) return Color.LightGray
        return when (urgency?.lowercase()) {
            "critical" -> Color.Red
            "high" -> Color(0xFFFFA500)    // Orange
            "due" -> Color(0xFFFFD700)     // Gold
            else -> Color(0xFF4CAF50)      // Green for routine
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Service Eligibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "$eligibleCount/$totalCount Eligible",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        "Loading service eligibility...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!errorMessage.isNullOrBlank()) {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (serviceEligibility.isEmpty()) {
                Text(
                    "No service eligibility data available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                serviceOrder.forEachIndexed { idx, serviceName ->
                    val eligibility = serviceEligibility[serviceName]
                    if (eligibility != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    displayNames[serviceName] ?: serviceName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    eligibility.reason ?: "No details available",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (eligibility.urgency != null && eligibility.eligible) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Priority: ${eligibility.urgency}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = getUrgencyColor(eligibility.urgency, true),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (eligibility.eligible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    if (eligibility.eligible) "Eligible" else "Not Eligible",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (eligibility.eligible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (idx < serviceOrder.size - 1) HorizontalDivider()
                    }
                }
            }
        }
    }
}