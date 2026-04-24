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
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.PatientProfileViewModel
import com.carecompanion.presentation.viewmodels.SharedViewModel
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Biometric Enrollment", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (uiState.hasBiometric) "Client has biometric" else "Client has no biometric",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (uiState.hasBiometric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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

                item {
                    ServiceEligibilitySection(uiState.artPharmacy)
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
            patient.ninNumber?.let { InfoRow("NIN", it) }
            patient.phoneNumber?.let { InfoRow("Phone", it) }
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

private data class ServiceEligibilityItem(
    val name: String,
    val eligible: Boolean,
    val note: String
)

@Composable
private fun ServiceEligibilitySection(artRecords: List<ArtPharmacy>) {
    val latest = artRecords.maxByOrNull { it.visitDate?.time ?: Long.MIN_VALUE }
    val daysOverdue = latest?.nextAppointment?.let {
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it.time).toInt().coerceAtLeast(0)
    } ?: 0
    val iitEligible = latest?.nextAppointment != null && daysOverdue >= 28

    val items = listOf(
        ServiceEligibilityItem("IIT", iitEligible, if (iitEligible) "Eligible: missed refill >= 28 days" else "Not eligible"),
        ServiceEligibilityItem("ART Refills", false, "Not eligible"),
        ServiceEligibilityItem("Missed Appointments", false, "Not eligible"),
        ServiceEligibilityItem("Viral Load", false, "Not eligible"),
        ServiceEligibilityItem("TPT", false, "Not eligible"),
        ServiceEligibilityItem("TB", false, "Not eligible"),
        ServiceEligibilityItem("AHD", false, "Not eligible")
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Service Eligibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Preview status. Full service-specific eligibility logic will be enabled as complete clinical data is integrated.",
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