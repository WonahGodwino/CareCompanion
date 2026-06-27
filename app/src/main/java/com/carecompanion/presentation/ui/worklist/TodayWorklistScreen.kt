package com.carecompanion.presentation.ui.worklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.database.entities.WorklistEntry
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.DueService
import com.carecompanion.presentation.viewmodels.TodayWorklistUiState
import com.carecompanion.presentation.viewmodels.TodayWorklistViewModel
import com.carecompanion.presentation.viewmodels.WorklistUiEntry
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.RegimenLookup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayWorklistScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    viewModel: TodayWorklistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today's Clinic", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge)
                        if (!uiState.isLoading) {
                            Text("${uiState.totalCount} expected · ${uiState.todayLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF01579B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showScrollTop, enter = fadeIn(), exit = fadeOut()) {
                SmallFloatingActionButton(
                    onClick = { /* scroll handled by listState */ },
                    containerColor = Color(0xFF01579B), contentColor = Color.White
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            uiState.entries.isEmpty() -> EmptyState(Modifier.fillMaxSize().padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Summary chips row
                    val serviceCounts = uiState.entries
                        .flatMap { it.dueServices }
                        .groupingBy { it }
                        .eachCount()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DueService.values().forEach { svc ->
                            serviceCounts[svc]?.let { count ->
                                ServiceCountChip(service = svc, count = count)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                items(uiState.entries, key = { it.entry.uuid }) { uiEntry ->
                    WorklistCard(
                        uiEntry = uiEntry,
                        onClick = {
                            navController.navigate(
                                Screen.PatientProfile.createRoute(uiEntry.entry.uuid)
                            )
                        }
                    )
                }
            }
        }
    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun ServiceCountChip(service: DueService, count: Int) {
    val color = Color(service.colorHex)
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
            )
            Text("$count ${service.shortLabel}", fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun WorklistCard(uiEntry: WorklistUiEntry, onClick: () -> Unit) {
    val e = uiEntry.entry
    val apptTime = e.nextAppointment.let { DateUtils.formatTime(it) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left time bar
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .background(Color(0xFF01579B))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(4.dp))
                Text(apptTime, color = Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                // Patient name + avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e.initial.toString(), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.displayName, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("HN: ${e.hospitalNumber}  ·  ${e.sex ?: ""}  ·  ${
                            e.dateOfBirth?.let { "Age ${DateUtils.calculateAge(it)}" } ?: ""
                        }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Regimen
                e.regimenId?.let { id ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Medication, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = Color(0xFF01579B))
                        Text(RegimenLookup.getDisplayName(id),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF01579B), fontWeight = FontWeight.Medium)
                    }
                }

                // Due services chips
                if (uiEntry.dueServices.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiEntry.dueServices.forEach { svc ->
                            val color = Color(svc.colorHex)
                            Surface(
                                color = color.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(svc.shortLabel,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                    }
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 12.dp).size(20.dp))
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Color(0xFF01579B))
            Text("Loading today's appointments…", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(Icons.Default.EventAvailable, contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f))
            Text("No Appointments Today", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("No patients are scheduled for today. Check Missed Appointments for clients who may still present.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                textAlign = TextAlign.Center)
        }
    }
}
