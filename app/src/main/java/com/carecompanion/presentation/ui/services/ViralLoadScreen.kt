package com.carecompanion.presentation.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.ViralLoadClientItem
import com.carecompanion.presentation.viewmodels.ViralLoadCurrentUiState
import com.carecompanion.presentation.viewmodels.ViralLoadTab
import com.carecompanion.presentation.viewmodels.ViralLoadViewModel
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.ViralLoadDueType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViralLoadScreen(
    navController: NavController,
    viewModel: ViralLoadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Viral Load Service",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Professional due tracking and missed sample monitoring",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search by name or hospital number") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip(
                        "Due Window",
                        uiState.dueTodayCount + uiState.dueThisWeekCount + uiState.dueThisMonthCount,
                        Color(0xFFE3F2FD),
                        Color(0xFF0D47A1)
                    )
                    SummaryChip(
                        "Pending Result",
                        uiState.pendingResultCount,
                        Color(0xFFFFF3E0),
                        Color(0xFFE65100)
                    )
                    SummaryChip(
                        "Overdue Pending",
                        uiState.overduePendingCount,
                        Color(0xFFFFEBEE),
                        Color(0xFFB71C1C)
                    )
                }
            }

            item {
                ViralLoadTabsWithArrows(
                    selectedTab = uiState.selectedTab,
                    dueTodayCount = uiState.dueTodayCount,
                    dueThisWeekCount = uiState.dueThisWeekCount,
                    dueThisMonthCount = uiState.dueThisMonthCount,
                    pendingResultCount = uiState.pendingResultCount,
                    overduePendingCount = uiState.overduePendingCount,
                    onTabSelected = viewModel::selectTab
                )
            }

            if (uiState.clients.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Biotech, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when (uiState.selectedTab) {
                                    ViralLoadTab.TODAY -> "No patients due today for viral load service."
                                    ViralLoadTab.THIS_WEEK -> "No patients due this week for viral load service."
                                    ViralLoadTab.THIS_MONTH -> "No patients due this month for viral load service."
                                    ViralLoadTab.PENDING_RESULT -> "No patients with pending viral load results."
                                    ViralLoadTab.OVERDUE_PENDING -> "No patients with overdue pending viral load results."
                                }
                            )
                        }
                    }
                }
            } else {
                items(uiState.clients) { item ->
                    ViralLoadClientCard(item, navController)
                }
            }
        }
    }
}

@Composable
private fun ViralLoadCurrentCard(
    item: ViralLoadClientItem,
    navController: NavController,
    patientId: String
) {
    val current = item.toCurrentViralLoadUiState()
    val palette = viralLoadPalette(current.resultLabel, current.statusLabel)
    val dueTypeText = if (item.eligibility.dueType == ViralLoadDueType.BASELINE) "Baseline" else "Routine"
    val dueColor = if (item.eligibility.dueType == ViralLoadDueType.BASELINE) Color(0xFF2E7D32) else Color(0xFFE65100)
    val dueBg = if (item.eligibility.dueType == ViralLoadDueType.BASELINE) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val patientName = item.patient.fullName ?: listOfNotNull(item.patient.surname, item.patient.firstName).joinToString(" ")
    val sampleDateValue = current.sampleDateLabel.substringAfter(": ", current.sampleDateLabel)
    val resultDateValue = current.resultDateLabel.substringAfter(": ", current.resultDateLabel)
    val dueDateValue = current.nextDueLabel.substringAfter(": ", current.nextDueLabel)

    val badgeIcon = when (current.resultLabel) {
        "Unsuppressed" -> Icons.Default.Warning
        "Undetected"   -> Icons.Default.CheckCircle
        "Suppressed"   -> Icons.Default.VerifiedUser
        else           -> Icons.Default.Science
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                navController.navigate(Screen.PatientProfile.createRoute(patientId))
            },
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            current.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.onContainer
                        )
                        Text(
                            patientName,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onContainer.copy(alpha = 0.80f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        dueTypeText,
                        modifier = Modifier
                            .background(dueBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = dueColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
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

                Surface(
                    color = palette.onContainer.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Hospital No: ${item.patient.hospitalNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "ART Registration: ${DateUtils.formatDate(item.patient.dateOfRegistration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CalendarToday, null, tint = palette.onContainer.copy(alpha = 0.70f), modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sample Date", style = MaterialTheme.typography.labelSmall, color = palette.onContainer.copy(alpha = 0.70f), fontWeight = FontWeight.SemiBold)
                            Text(sampleDateValue, style = MaterialTheme.typography.bodySmall, color = palette.onContainer)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Event, null, tint = palette.onContainer.copy(alpha = 0.70f), modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Result Date", style = MaterialTheme.typography.labelSmall, color = palette.onContainer.copy(alpha = 0.70f), fontWeight = FontWeight.SemiBold)
                            Text(resultDateValue, style = MaterialTheme.typography.bodySmall, color = palette.onContainer)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Today, null, tint = palette.onContainer.copy(alpha = 0.70f), modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Next Due Date", style = MaterialTheme.typography.labelSmall, color = palette.onContainer.copy(alpha = 0.70f), fontWeight = FontWeight.SemiBold)
                            Text(dueDateValue, style = MaterialTheme.typography.bodySmall, color = palette.onContainer)
                        }
                    }
                }

                current.flagLabel?.let { flag ->
                    Surface(
                        color = palette.flagContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = palette.onFlagContainer, modifier = Modifier.size(16.dp))
                            Text(
                                flag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.onFlagContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.onContainer.copy(alpha = 0.06f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.TouchApp, null, tint = palette.onContainer.copy(alpha = 0.60f), modifier = Modifier.size(14.dp))
                    Text(
                        "Tap to view patient profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onContainer.copy(alpha = 0.70f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ViralLoadTabsWithArrows(
    selectedTab: ViralLoadTab,
    dueTodayCount: Int,
    dueThisWeekCount: Int,
    dueThisMonthCount: Int,
    pendingResultCount: Int,
    overduePendingCount: Int,
    onTabSelected: (ViralLoadTab) -> Unit
) {
    val tabs = listOf(
        Triple(ViralLoadTab.TODAY, "Due Today", dueTodayCount),
        Triple(ViralLoadTab.THIS_WEEK, "This Week", dueThisWeekCount),
        Triple(ViralLoadTab.THIS_MONTH, "This Month", dueThisMonthCount),
        Triple(ViralLoadTab.PENDING_RESULT, "Pending Result", pendingResultCount),
        Triple(ViralLoadTab.OVERDUE_PENDING, "Overdue Pending", overduePendingCount),
    )

    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        tabs.forEach { (tab, title, count) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        "$title ($count)",
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium
                    )
                },
                icon = {
                    when (tab) {
                        ViralLoadTab.TODAY -> Icon(Icons.Default.Today, contentDescription = null)
                        ViralLoadTab.THIS_WEEK -> Icon(Icons.Default.DateRange, contentDescription = null)
                        ViralLoadTab.THIS_MONTH -> Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        ViralLoadTab.PENDING_RESULT -> Icon(Icons.Default.Biotech, contentDescription = null)
                        ViralLoadTab.OVERDUE_PENDING -> Icon(Icons.Default.WarningAmber, contentDescription = null)
                    }
                }
            )
        }
    }
}

@Composable
private fun SummaryChip(title: String, count: Int, bg: Color, fg: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(58.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = fg)
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ViralLoadClientCard(item: ViralLoadClientItem, navController: NavController) {
    ViralLoadCurrentCard(
        item = item,
        navController = navController,
        patientId = item.patient.personUuid ?: item.patient.uuid
    )
}

private fun ViralLoadClientItem.toCurrentViralLoadUiState(): ViralLoadCurrentUiState {
    val parsedResult = latestViralLoadResult?.trim()?.toDoubleOrNull()
    val pending = statusLabel.contains("pending", ignoreCase = true)
    val overduePending = statusLabel.contains("overdue", ignoreCase = true) && pending

    val resultLabel = when {
        pending -> "Result Pending"
        parsedResult != null && parsedResult <= 20 -> "Undetected"
        parsedResult != null && parsedResult < 1000 -> "Suppressed"
        parsedResult != null -> "Unsuppressed"
        latestViralLoadResult.isNullOrBlank() -> statusLabel
        else -> latestViralLoadResult
    }

    val resultValueLabel = when {
        pending -> "Pending"
        parsedResult != null -> parsedResult.toInt().toString()
        latestViralLoadResult.isNullOrBlank() -> "N/A"
        else -> latestViralLoadResult
    }

    return ViralLoadCurrentUiState(
        statusLabel = if (pending) "Viral Load Sample Collection" else "Current Viral Load",
        resultLabel = resultLabel,
        resultValueLabel = resultValueLabel,
        sampleDateLabel = sampleCollectionDate?.let { "Sample Date: ${DateUtils.formatDate(it)}" } ?: "Sample Date: N/A",
        resultDateLabel = resultReportedDate?.let { "Result Date: ${DateUtils.formatDate(it)}" } ?: "Result Date: N/A",
        nextDueLabel = "Next Due Date: ${DateUtils.formatDate(eligibility.dueDate)}",
        flagLabel = flagLabel,
        dueType = eligibility.dueType,
        eligibility = eligibility,
        isPendingResult = pending,
        isOverduePendingResult = overduePending,
    )
}

internal data class ViralLoadPalette(
    val container: Color,
    val onContainer: Color,
    val flagContainer: Color,
    val onFlagContainer: Color,
)

internal fun viralLoadPalette(resultLabel: String, statusLabel: String): ViralLoadPalette {
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
