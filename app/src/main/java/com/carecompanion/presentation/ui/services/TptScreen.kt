package com.carecompanion.presentation.ui.services

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.database.entities.TptEntry
import com.carecompanion.data.database.entities.TptStatus
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.TptUiState
import com.carecompanion.presentation.viewmodels.TptViewModel
import com.carecompanion.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TptScreen(
    navController: NavController,
    viewModel: TptViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf(
        "Not Screened" to state.notScreened.size,
        "TPT Eligible" to state.eligible.size,
        "On IPT"       to state.onIpt.size,
        "TB Positive"  to state.tbPositive.size
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("TB Preventive Therapy", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge)
                            if (!state.isLoading) {
                                Text("${state.totalCount} active ART clients",
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
                        containerColor = Color(0xFF00695C),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )

                // Search bar
                Surface(color = Color(0xFF00695C), shadowElevation = 4.dp) {
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

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { idx, (label, count) ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick  = { selectedTab = idx },
                            text = {
                                Text(
                                    "$label ($count)",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val displayList = when (selectedTab) {
            0 -> state.notScreened
            1 -> state.eligible
            2 -> state.onIpt
            3 -> state.tbPositive
            else -> emptyList()
        }
        val (tabColor, tabStatus) = when (selectedTab) {
            0 -> Color(0xFFE65100) to TptStatus.NOT_SCREENED
            1 -> Color(0xFF00695C) to TptStatus.ELIGIBLE
            2 -> Color(0xFF1565C0) to TptStatus.ON_IPT
            3 -> Color(0xFFC62828) to TptStatus.TB_POSITIVE
            else -> MaterialTheme.colorScheme.primary to TptStatus.NOT_SCREENED
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00695C))
            }
            displayList.isEmpty() -> TptEmptyState(
                status = tabStatus,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TptStatusBanner(status = tabStatus)
                    Spacer(Modifier.height(4.dp))
                }
                items(displayList, key = { it.uuid }) { entry ->
                    TptEntryCard(
                        entry = entry,
                        accentColor = tabColor,
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
private fun TptStatusBanner(status: TptStatus) {
    val (bgColor, textColor, icon) = when (status) {
        TptStatus.NOT_SCREENED   -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Warning)
        TptStatus.ELIGIBLE       -> Triple(Color(0xFFE0F2F1), Color(0xFF00695C), Icons.Default.HealthAndSafety)
        TptStatus.ON_IPT         -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), Icons.Default.Medication)
        TptStatus.TB_POSITIVE    -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.ReportProblem)
        TptStatus.SCREENED_OTHER -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), Icons.Default.HelpOutline)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            Text(status.description, style = MaterialTheme.typography.bodySmall,
                color = textColor, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun TptEntryCard(entry: TptEntry, accentColor: Color, onClick: () -> Unit) {
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
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entry.initial.toString(), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = accentColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("HN: ${entry.hospitalNumber}  ·  ${entry.sex ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entry.lastTbScreeningDate?.let { date ->
                        Text("TB Screened: ${DateUtils.formatDate(date)} · ${entry.lastTbScreeningStatus ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = accentColor,
                            fontWeight = FontWeight.Medium)
                    } ?: Text("Never screened for TB",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
                    entry.iptType?.let { ipt ->
                        Text("IPT: $ipt", style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1565C0), fontWeight = FontWeight.Medium)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.30f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun TptEmptyState(status: TptStatus, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(Icons.Default.HealthAndSafety, contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.40f))
            Text("No patients in this category", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(status.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.70f), textAlign = TextAlign.Center)
        }
    }
}
