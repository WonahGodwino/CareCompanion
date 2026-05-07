package com.carecompanion.presentation.ui.services

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.VLClientItem
import com.carecompanion.presentation.viewmodels.ViralLoadViewModel
import com.carecompanion.utils.DateUtils
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// Group metadata
// ─────────────────────────────────────────────────────────────────────────────
private data class VLGroupMeta(
    val key: String,
    val label: String,
    val bg: Color,
    val fg: Color,
)

private val VL_GROUP_METAS = listOf(
    VLGroupMeta("ACTIVE_ELIGIBLE_BASELINE", "Baseline VL Due",    Color(0xFFFFEBEE), Color(0xFFC62828)),
    VLGroupMeta("ACTIVE_ELIGIBLE_ROUTINE",  "Routine VL Due",     Color(0xFFFFF3E0), Color(0xFFE65100)),
    VLGroupMeta("ACTIVE_NOT_DUE",           "Not Yet Due",        Color(0xFFE3F2FD), Color(0xFF01579B)),
    VLGroupMeta("ACTIVE_NO_VL_DATA",        "No VL Data",         Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
    VLGroupMeta("IIT",                      "IIT",                Color(0xFFFFEBEE), Color(0xFFC62828)),
    VLGroupMeta("TRANSFER_OUT",             "Transfer Out",       Color(0xFFECEFF1), Color(0xFF546E7A)),
    VLGroupMeta("STOPPED_TREATMENT",        "Stopped Treatment",  Color(0xFFF3E5F5), Color(0xFF4A148C)),
    VLGroupMeta("DEATH",                    "Death",              Color(0xFFEEEEEE), Color(0xFF424242)),
    VLGroupMeta("OTHER_INACTIVE",           "Other Inactive",     Color(0xFFFAFAFA), Color(0xFF757575)),
    VLGroupMeta("UNKNOWN_STATUS",           "Unknown Status",     Color(0xFFFFF9C4), Color(0xFFF57F17)),
)
private val VL_META_BY_KEY = VL_GROUP_METAS.associateBy { it.key }

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViralLoadScreen(
    navController: NavController,
    viewModel: ViralLoadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val displayedGroups = VL_GROUP_METAS.filter { (uiState.groupedByEligibility[it.key]?.size ?: 0) > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Viral Load", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "VL Suppression & Testing Eligibility",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.80f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = Color(0xFF6A1B9A),
                    titleContentColor          = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor     = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showFab, enter = fadeIn(), exit = fadeOut()) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = Color(0xFF6A1B9A),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ───────────────────────────────────────────────────
            Surface(color = Color(0xFF6A1B9A), modifier = Modifier.fillMaxWidth()) {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                    shape     = RoundedCornerShape(12.dp),
                ) {
                    OutlinedTextField(
                        value         = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder   = { Text("Search by name or hospital number...") },
                        leadingIcon   = { Icon(Icons.Default.Search, null, tint = Color(0xFF6A1B9A)) },
                        trailingIcon  = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }
                        },
                        singleLine = true,
                        modifier   = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        shape      = RoundedCornerShape(10.dp),
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFF6A1B9A),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        )
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF6A1B9A))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading viral load data...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {

                    // ── Info banner ──────────────────────────────────────────
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            colors   = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                            shape    = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(18.dp))
                                Text(
                                    "Baseline VL: due 6 months (181 days) after ART start for clients with no VL on record. " +
                                    "Routine VL: every 12 months for adults (>14 yrs) and 6 months for children. " +
                                    "Eligibility groups align with the PEPFAR VL cascade framework.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4A148C),
                                )
                            }
                        }
                    }

                    // ── Summary row ──────────────────────────────────────────
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Total: ${uiState.totalCount}  |  Due: ${uiState.eligibleBaselineCount + uiState.eligibleRoutineCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (selectedGroup != null) {
                                    TextButton(onClick = { selectedGroup = null }) {
                                        Text("Show all", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // ── Filter chips ─────────────────────────────────────────
                    item {
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(VL_GROUP_METAS) { meta ->
                                val count = uiState.groupedByEligibility[meta.key]?.size ?: 0
                                if (count == 0) return@items
                                val isSelected = selectedGroup == meta.key
                                FilterChip(
                                    selected = isSelected,
                                    onClick  = { selectedGroup = if (isSelected) null else meta.key },
                                    label    = { Text("${meta.label}: $count", fontSize = 12.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = meta.fg,
                                        selectedLabelColor     = Color.White,
                                        containerColor         = meta.bg,
                                        labelColor             = meta.fg,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Client groups ────────────────────────────────────────
                    val groupsToShow = if (selectedGroup != null)
                        VL_GROUP_METAS.filter { it.key == selectedGroup }
                    else
                        displayedGroups

                    groupsToShow.forEach { meta ->
                        val clients = uiState.groupedByEligibility[meta.key] ?: emptyList()
                        if (clients.isEmpty()) return@forEach
                        val isExp = expanded[meta.key] ?: true

                        item(key = "header_${meta.key}") {
                            VLGroupHeader(meta = meta, count = clients.size, expanded = isExp, onToggle = {
                                expanded[meta.key] = !isExp
                            })
                        }

                        if (isExp) {
                            items(items = clients, key = { it.patient.uuid }) { item ->
                                VLClientRow(
                                    item           = item,
                                    meta           = meta,
                                    onPatientClick = { navController.navigate(Screen.PatientProfile.createRoute(item.patient.uuid)) },
                                )
                            }
                        }
                    }

                    if (displayedGroups.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (uiState.searchQuery.isNotEmpty())
                                        "No clients match \"${uiState.searchQuery}\""
                                    else
                                        "No ART clients found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Group header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VLGroupHeader(
    meta: VLGroupMeta,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(meta.bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(meta.fg),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(meta.label, fontWeight = FontWeight.SemiBold, color = meta.fg, fontSize = 14.sp)
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = meta.fg,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VLClientRow(
    item: VLClientItem,
    meta: VLGroupMeta,
    onPatientClick: () -> Unit,
) {
    val patient = item.patient
    val displayName = patient.fullName?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(patient.surname?.trim(), patient.firstName?.trim())
            .filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotBlank() }
        ?: patient.hospitalNumber

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPatientClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(meta.fg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                fontWeight = FontWeight.SemiBold,
                style      = MaterialTheme.typography.bodyMedium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                "${patient.hospitalNumber}  •  ${patient.sex ?: "–"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Due date line
            item.dueDate?.let { due ->
                val todayMs = System.currentTimeMillis()
                val daysLeft = TimeUnit.MILLISECONDS.toDays(due.time - todayMs)
                val dueLabel = when {
                    daysLeft < 0  -> "${item.vlType.replaceFirstChar { it.uppercaseChar() }} VL: overdue by ${-daysLeft}d"
                    daysLeft == 0L -> "${item.vlType.replaceFirstChar { it.uppercaseChar() }} VL: due today"
                    else           -> "${item.vlType.replaceFirstChar { it.uppercaseChar() }} VL due in ${daysLeft}d (${DateUtils.formatDate(due)})"
                }
                Text(dueLabel, style = MaterialTheme.typography.bodySmall, color = meta.fg)
            }
            // Last VL result
            item.lastResultText?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Last VL date
            patient.lastViralLoadDate?.let {
                Text(
                    "Last VL: ${DateUtils.formatDate(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // VL type badge
        if (item.vlType != "none") {
            Surface(color = meta.bg, shape = RoundedCornerShape(6.dp)) {
                Text(
                    item.vlType.replaceFirstChar { it.uppercaseChar() },
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color      = meta.fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 11.sp,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}
