package com.carecompanion.presentation.ui.patient

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.RecallViewModel
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.utils.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    viewModel: RecallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll-to-top FAB visibility
    val showScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 4 }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Patient Directory",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (!uiState.isLoading) {
                                Text(
                                    "${uiState.totalCount} active clients",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor             = MaterialTheme.colorScheme.primary,
                        titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor     = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Sync.route) }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync")
                        }
                    }
                )

                // ── Persistent search bar directly under the app bar ────────
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = {
                            Text(
                                "Search by name or hospital number…",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.60f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = uiState.searchQuery.isNotEmpty(),
                                enter = fadeIn(),
                                exit  = fadeOut()
                            ) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.60f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.30f),
                            focusedTextColor     = MaterialTheme.colorScheme.onPrimary,
                            unfocusedTextColor   = MaterialTheme.colorScheme.onPrimary,
                            cursorColor          = MaterialTheme.colorScheme.onPrimary,
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showScrollTop, enter = fadeIn(), exit = fadeOut()) {
                SmallFloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Loading patients…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.patients.isEmpty() -> {
                PatientListEmptyState(
                    query = uiState.searchQuery,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Result count chip
                    if (uiState.searchQuery.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape  = MaterialTheme.shapes.small,
                                color  = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${uiState.patients.size} result${if (uiState.patients.size != 1) "s" else ""} for \"${uiState.searchQuery}\"",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style    = MaterialTheme.typography.labelMedium,
                                    color    = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state   = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start  = 16.dp,
                            end    = 16.dp,
                            top    = 8.dp,
                            bottom = 88.dp  // room for FAB
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.patients, key = { it.uuid }) { patient ->
                            PatientListItem(
                                patient = patient,
                                onClick = {
                                    sharedViewModel.setSelectedPatient(patient)
                                    navController.navigate(Screen.PatientProfile.createRoute(patient.uuid))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Error dialog
    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            icon  = { Icon(Icons.Default.ErrorOutline, contentDescription = null) },
            title = { Text("Error") },
            text  = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Patient list item card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PatientListItem(patient: Patient, onClick: () -> Unit) {
    val initial = (patient.firstName?.firstOrNull()
        ?: patient.fullName?.firstOrNull()
        ?: '?').toString().uppercase()

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape     = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Patient info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = patient.fullName
                        ?: "${patient.firstName ?: ""} ${patient.surname ?: ""}".trim()
                            .ifEmpty { "Unknown" },
                    style    = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hospital number badge
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text  = "HN: ${patient.hospitalNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    patient.sex?.let { sex ->
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (sex.equals("Male", ignoreCase = true))
                                        Color(0xFFE3F2FD) else Color(0xFFFCE4EC)
                        ) {
                            Text(
                                text  = sex,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sex.equals("Male", ignoreCase = true))
                                            Color(0xFF01579B) else Color(0xFF880E4F),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                patient.dateOfBirth?.let { dob ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = "Age ${DateUtils.calculateAge(dob)} · DOB ${DateUtils.formatDate(dob)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PatientListEmptyState(query: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                imageVector = if (query.isBlank()) Icons.Default.PeopleAlt else Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = if (query.isBlank())
                    "No patients found"
                else
                    "No results for",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (query.isNotBlank()) {
                Text(
                    text = "\"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = if (query.isBlank())
                    "Sync data from the EMR to populate the patient list."
                else
                    "Try a different name or hospital number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
