package com.carecompanion.presentation.ui.patient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.database.entities.NoBiometricEntry
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.UnregisteredBiometricViewModel
import com.carecompanion.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnregisteredBiometricScreen(
    navController: NavController,
    viewModel: UnregisteredBiometricViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Biometric Enrollment Gap", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge)
                            if (!state.isLoading) {
                                Text("${state.totalCount} clients without biometrics",
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
                        containerColor = Color(0xFFC62828),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )

                Surface(color = Color(0xFFC62828), shadowElevation = 4.dp) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = {
                            Text("Search by name or hospital number…",
                                color = Color.White.copy(alpha = 0.60f),
                                style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(0.80f)) },
                        trailingIcon = {
                            AnimatedVisibility(state.searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(Icons.Default.Clear, null, tint = Color.White)
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color.White.copy(0.60f),
                            unfocusedBorderColor = Color.White.copy(0.30f),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            cursorColor          = Color.White
                        )
                    )
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFC62828))
            }
            state.patients.isEmpty() -> EmptyState(
                query = state.searchQuery,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Fingerprint, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                            Text(
                                "These clients are not yet enrolled for biometric identification. " +
                                "Enroll them during their next clinic visit to improve identification accuracy.",
                                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C),
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                items(state.patients, key = { it.uuid }) { entry ->
                    NoBiometricCard(
                        entry = entry,
                        onClick = { navController.navigate(Screen.PatientProfile.createRoute(entry.uuid)) }
                    )
                }
            }
        }
    }

    state.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Error") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun NoBiometricCard(entry: NoBiometricEntry, onClick: () -> Unit) {
    val accentColor = Color(0xFFC62828)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(accentColor))
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entry.initial.toString(), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = accentColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("HN: ${entry.hospitalNumber}  ·  ${entry.sex ?: ""}  ·  ${
                        entry.dateOfBirth?.let { "Age ${DateUtils.calculateAge(it)}" } ?: ""
                    }", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null,
                            tint = accentColor, modifier = Modifier.size(14.dp))
                        Text("No biometrics enrolled",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor, fontWeight = FontWeight.Medium)
                    }
                    entry.artStartDate?.let { d ->
                        Text("ART Start: ${DateUtils.formatDate(d)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.30f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(query: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(
                if (query.isBlank()) Icons.Default.CheckCircle else Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = if (query.isBlank()) Color(0xFF2E7D32)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.40f)
            )
            Text(
                if (query.isBlank()) "All Clients Enrolled" else "No Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
            Text(
                if (query.isBlank()) "Every active client has at least one biometric enrolled. Excellent coverage!"
                else "No unregistered clients found for \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.70f),
                textAlign = TextAlign.Center
            )
        }
    }
}
