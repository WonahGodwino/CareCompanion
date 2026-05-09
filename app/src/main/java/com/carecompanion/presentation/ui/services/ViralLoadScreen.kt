package com.carecompanion.presentation.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.viewmodels.ViralLoadClientItem
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
                            "Viral Load Due Clients",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Eligibility from ART registration schedule",
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    SummaryChip("Total Due", uiState.totalDueCount, Color(0xFFE3F2FD), Color(0xFF1565C0))
                    SummaryChip("Baseline", uiState.baselineDueCount, Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    SummaryChip("Routine", uiState.routineDueCount, Color(0xFFFFF3E0), Color(0xFFE65100))
                }
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
                            Text("No clients are currently due for viral load collection.")
                        }
                    }
                }
            } else {
                items(uiState.clients) { item ->
                    ViralLoadClientCard(item)
                }
            }
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
private fun ViralLoadClientCard(item: ViralLoadClientItem) {
    val dueTypeText = if (item.eligibility.dueType == ViralLoadDueType.BASELINE) "Baseline" else "Routine"
    val dueColor = if (item.eligibility.dueType == ViralLoadDueType.BASELINE) Color(0xFF2E7D32) else Color(0xFFE65100)
    val dueBg = if (item.eligibility.dueType == ViralLoadDueType.BASELINE) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val overdueText = if (item.eligibility.daysOverdue > 0) {
        "${item.eligibility.daysOverdue} day(s) overdue"
    } else {
        "Due today"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.patient.fullName ?: listOfNotNull(item.patient.surname, item.patient.firstName).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dueTypeText,
                    modifier = Modifier
                        .background(dueBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = dueColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text("Hospital No: ${item.patient.hospitalNumber}", style = MaterialTheme.typography.bodySmall)
            Text("ART Registration: ${DateUtils.formatDate(item.patient.dateOfRegistration)}", style = MaterialTheme.typography.bodySmall)
            Text("Due Date: ${DateUtils.formatDate(item.eligibility.dueDate)}", style = MaterialTheme.typography.bodySmall)
            Text("VL Status: ${item.statusLabel}", style = MaterialTheme.typography.bodySmall)
            item.latestViralLoadResult?.takeIf { it.isNotBlank() }?.let {
                Text("Latest Result: $it", style = MaterialTheme.typography.bodySmall)
            }
            item.flagLabel?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC62828), fontWeight = FontWeight.Medium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventBusy, contentDescription = null, tint = Color(0xFFC62828))
                Spacer(Modifier.width(6.dp))
                Text(overdueText, color = Color(0xFFC62828), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
