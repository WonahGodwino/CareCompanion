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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.IITPeriod
import com.carecompanion.presentation.viewmodels.MissedApptTier
import com.carecompanion.presentation.viewmodels.MissedApptUiState
import com.carecompanion.presentation.viewmodels.MissedApptViewModel
import com.carecompanion.presentation.viewmodels.missedApptTier
import com.carecompanion.presentation.viewmodels.missedDays
import com.carecompanion.utils.DateUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// ─── tier colours ─────────────────────────────────────────────────────────────
private val TierRecentBg   = Color(0xFFFFF8E1)
private val TierRecentFg   = Color(0xFFF9A825)
private val TierOverdueBg  = Color(0xFFFFF3E0)
private val TierOverdueFg  = Color(0xFFE65100)
private val TierHighBg     = Color(0xFFFFEBEE)
private val TierHighFg     = Color(0xFFC62828)
private val TierIitBg      = Color(0xFFF3E5F5)
private val TierIitFg      = Color(0xFF6A1B9A)

private fun tierBg(tier: MissedApptTier) = when (tier) {
    MissedApptTier.RECENT        -> TierRecentBg
    MissedApptTier.OVERDUE       -> TierOverdueBg
    MissedApptTier.HIGH_PRIORITY -> TierHighBg
    MissedApptTier.IIT_CONFIRMED -> TierIitBg
}
private fun tierFg(tier: MissedApptTier) = when (tier) {
    MissedApptTier.RECENT        -> TierRecentFg
    MissedApptTier.OVERDUE       -> TierOverdueFg
    MissedApptTier.HIGH_PRIORITY -> TierHighFg
    MissedApptTier.IIT_CONFIRMED -> TierIitFg
}
private fun avatarBg(tier: MissedApptTier) = tierFg(tier)

// ─── period colours (same palette as IIT) ─────────────────────────────────────
private fun periodColor(period: IITPeriod) = when (period) {
    IITPeriod.TODAY      -> Color(0xFFB71C1C)
    IITPeriod.THIS_WEEK  -> Color(0xFFC62828)
    IITPeriod.LAST_WEEK  -> Color(0xFFE64A19)
    IITPeriod.THIS_MONTH -> Color(0xFFE65100)
    IITPeriod.THIS_FY    -> Color(0xFF1565C0)
    IITPeriod.PREVIOUS   -> Color(0xFF546E7A)
}
private fun periodBg(period: IITPeriod) = when (period) {
    IITPeriod.TODAY      -> Color(0xFFFFEBEE)
    IITPeriod.THIS_WEEK  -> Color(0xFFFFF8F8)
    IITPeriod.LAST_WEEK  -> Color(0xFFFFF3E0)
    IITPeriod.THIS_MONTH -> Color(0xFFFFF8E1)
    IITPeriod.THIS_FY    -> Color(0xFFE3F2FD)
    IITPeriod.PREVIOUS   -> Color(0xFFECEFF1)
}

private val watTz = TimeZone.getTimeZone("Africa/Lagos")
private val shortFmt = SimpleDateFormat("MMM d", Locale.getDefault()).apply { timeZone = watTz }
private val fullFmt  = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply { timeZone = watTz }

// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedApptScreen(
    navController: NavController,
    viewModel: MissedApptViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    fun isExpanded(p: IITPeriod) = expanded[p.name] ?: true
    fun toggle(p: IITPeriod) { expanded[p.name] = !isExpanded(p) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Missed Appointments",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Clients who did not attend their scheduled appointment",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor     = MaterialTheme.colorScheme.onPrimary
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to top",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ──────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = {
                            Text("Search by name or hospital number…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(Icons.Default.Clear, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor   = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading missed appointments…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    // Info banner
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                Text(
                                    "Missed Appointment: clients who did not return on their scheduled next appointment date. " +
                                    "Tiers — Just Missed (1–6 days), Overdue (7–24 days), High Priority Tracking (25–27 days; 3 to 1 days to IIT), Already IIT (28+ days). " +
                                    "Clients are grouped by the date of the missed appointment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5D4037)
                                )
                            }
                        }
                    }

                    // Period summary chips
                    item { MissedApptPeriodSummaryRow(uiState) }

                    // Search result count
                    if (uiState.searchQuery.isNotBlank()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Search, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${uiState.clients.size} result${if (uiState.clients.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Grouped sections by period
                    IITPeriod.values().forEach { period ->
                        val periodClients = uiState.groupedByPeriod[period] ?: emptyList()
                        item(key = "header_${period.name}") {
                            MissedPeriodHeader(
                                period = period,
                                count = periodClients.size,
                                expanded = isExpanded(period),
                                onToggle = { toggle(period) }
                            )
                        }
                        if (isExpanded(period)) {
                            if (periodClients.isEmpty()) {
                                item(key = "empty_${period.name}") {
                                    Text(
                                        "No missed appointments in ${period.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(periodClients, key = { _, c -> c.patientId }) { index, client ->
                                    MissedApptClientRow(
                                        client = client,
                                        alternate = index % 2 == 1,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Grand total card
                    item {
                        MissedApptGrandTotalCard(
                            total   = uiState.totalCount,
                            recent  = uiState.recentCount,
                            overdue = uiState.overdueCount,
                            highPriority = uiState.highPriorityCount,
                            iitConfirmed = uiState.iitConfirmedCount,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Error") },
            text  = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

// ─── Period summary row ───────────────────────────────────────────────────────
@Composable
private fun MissedApptPeriodSummaryRow(state: MissedApptUiState) {
    val counts = listOf(
        IITPeriod.TODAY      to state.todayCount,
        IITPeriod.THIS_WEEK  to state.thisWeekCount,
        IITPeriod.LAST_WEEK  to state.lastWeekCount,
        IITPeriod.THIS_MONTH to state.thisMonthCount,
        IITPeriod.THIS_FY    to state.thisFYCount,
        IITPeriod.PREVIOUS   to state.previousCount,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(counts) { (period, count) ->
            val fg = periodColor(period)
            val bg = periodBg(period)
            Card(
                colors = CardDefaults.cardColors(containerColor = bg),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$count", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = fg)
                    Text(period.label, style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────
@Composable
private fun MissedPeriodHeader(
    period: IITPeriod,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val fg = periodColor(period)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.width(4.dp).height(36.dp)
                .clip(RoundedCornerShape(2.dp)).background(fg))
            Column(modifier = Modifier.weight(1f)) {
                Text(period.label, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = fg)
                Text("$count client${if (count == 1) "" else "s"} · appointment date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(fg.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null, tint = fg, modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

// ─── Client row ───────────────────────────────────────────────────────────────
@Composable
private fun MissedApptClientRow(
    client: IITClient,
    alternate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tier  = client.missedApptTier()
    val days  = client.missedDays()
    val daysToIit = (28 - days).coerceAtLeast(0)
    val bg    = tierBg(tier)
    val fg    = tierFg(tier)
    val av    = avatarBg(tier)
    val rowBg = if (alternate) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surface

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = rowBg)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(8.dp).fillMaxHeight().background(av))
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(av)
                ) {
                    Text("${client.initial}", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                // Info column
                Column(modifier = Modifier.weight(1f)) {
                    Text(client.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(client.hospitalNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp)
                        }
                        client.sex?.takeIf { it.isNotBlank() }?.let { sex ->
                            val sexBg = if (sex.lowercase().startsWith("m")) Color(0xFFE3F2FD) else Color(0xFFFCE4EC)
                            val sexFg = if (sex.lowercase().startsWith("m")) Color(0xFF1565C0) else Color(0xFFC2185B)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                    .background(sexBg).padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(sex.uppercase().take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sexFg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Last visit: ${DateUtils.formatDate(client.lastVisitDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Missed appt: ${DateUtils.formatDate(client.nextAppointment)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = fg)
                    if (tier == MissedApptTier.HIGH_PRIORITY) {
                        Text(
                            text = "IIT in $daysToIit day${if (daysToIit == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TierHighFg,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    client.dsdModel?.takeIf { it.isNotBlank() }?.let { dsd ->
                        Text("DSD: $dsd",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Box(modifier = Modifier.width(1.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))

                // Days badge + tier label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("$days", fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp, color = fg)
                            Text("days", fontSize = 9.sp, color = fg.copy(alpha = 0.8f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(av).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tier.shortLabel, fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    if (tier == MissedApptTier.HIGH_PRIORITY) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFCDD2))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "IIT in $daysToIit",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TierHighFg
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Grand total card ─────────────────────────────────────────────────────────
@Composable
private fun MissedApptGrandTotalCard(
    total: Int,
    recent: Int,
    overdue: Int,
    highPriority: Int,
    iitConfirmed: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.EventBusy, null,
                    tint = Color(0xFF6A1B9A), modifier = Modifier.size(20.dp))
                Text("Total Missed Appointments: $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TierSummaryChip("Just Missed", recent, TierRecentFg, TierRecentBg, Modifier.weight(1f))
                TierSummaryChip("Overdue",     overdue, TierOverdueFg, TierOverdueBg, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TierSummaryChip("High Priority", highPriority, TierHighFg, TierHighBg, Modifier.weight(1f))
                TierSummaryChip("Already IIT", iitConfirmed, TierIitFg, TierIitBg, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TierSummaryChip(
    label: String, count: Int,
    fg: Color, bg: Color,
    modifier: Modifier = Modifier
) {
    Card(colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
        }
    }
}
