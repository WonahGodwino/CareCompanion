package com.carecompanion.presentation.ui.services

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.carecompanion.data.database.entities.IITTier
import com.carecompanion.presentation.viewmodels.IITUiState
import com.carecompanion.presentation.viewmodels.IITViewModel
import com.carecompanion.utils.DateUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val TierEarlyBg = Color(0xFFFFF3E0)
private val TierEarlyFg = Color(0xFFE65100)
private val TierIITBg = Color(0xFFFFEBEE)
private val TierIITFg = Color(0xFFC62828)
private val TierLTFUFg = Color(0xFF8D1C1C)

private fun tierBg(tier: IITTier) = when (tier) {
    IITTier.EARLY_IIT -> TierEarlyBg
    IITTier.IIT -> TierIITBg
    IITTier.LTFU -> Color(0xFFF9EBEB)
}

private fun tierFg(tier: IITTier) = when (tier) {
    IITTier.EARLY_IIT -> TierEarlyFg
    IITTier.IIT -> TierIITFg
    IITTier.LTFU -> TierLTFUFg
}

private fun avatarBg(tier: IITTier) = when (tier) {
    IITTier.EARLY_IIT -> TierEarlyFg
    IITTier.IIT -> TierIITFg
    IITTier.LTFU -> TierLTFUFg
}

private fun periodColor(period: IITPeriod) = when (period) {
    IITPeriod.TODAY -> Color(0xFFB71C1C)
    IITPeriod.THIS_WEEK -> Color(0xFFC62828)
    IITPeriod.LAST_WEEK -> Color(0xFFE64A19)
    IITPeriod.THIS_MONTH -> Color(0xFFE65100)
    IITPeriod.THIS_FY -> Color(0xFF1565C0)
    IITPeriod.PREVIOUS -> Color(0xFF546E7A)
}

private fun periodBg(period: IITPeriod) = when (period) {
    IITPeriod.TODAY -> Color(0xFFFFEBEE)
    IITPeriod.THIS_WEEK -> Color(0xFFFFF8F8)
    IITPeriod.LAST_WEEK -> Color(0xFFFFF3E0)
    IITPeriod.THIS_MONTH -> Color(0xFFFFF8E1)
    IITPeriod.THIS_FY -> Color(0xFFE3F2FD)
    IITPeriod.PREVIOUS -> Color(0xFFECEFF1)
}

private val watTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos")
private val shortMonFmt = SimpleDateFormat("MMM d", Locale.getDefault()).apply { timeZone = watTimeZone }
private val fullMonFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).apply { timeZone = watTimeZone }
private val fullDateFmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).apply { timeZone = watTimeZone }

private fun periodDateRange(period: IITPeriod): String {
    val today = Calendar.getInstance(watTimeZone)
    val dow = today.get(Calendar.DAY_OF_WEEK)
    val daysSinceMon = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY

    return when (period) {
        IITPeriod.TODAY -> fullDateFmt.format(today.time)
        IITPeriod.THIS_WEEK -> {
            val mon = (today.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -daysSinceMon) }
            val sun = (mon.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, 6) }
            "${shortMonFmt.format(mon.time)} - ${shortMonFmt.format(sun.time)}, ${today.get(Calendar.YEAR)}"
        }
        IITPeriod.LAST_WEEK -> {
            val thisMon = (today.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -daysSinceMon) }
            val lastMon = (thisMon.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -7) }
            val lastSun = (thisMon.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -1) }
            "${shortMonFmt.format(lastMon.time)} - ${shortMonFmt.format(lastSun.time)}, ${lastSun.get(Calendar.YEAR)}"
        }
        IITPeriod.THIS_MONTH -> fullMonFmt.format(today.time)
        IITPeriod.THIS_FY -> {
            val fyYear = if (today.get(Calendar.MONTH) >= Calendar.OCTOBER) today.get(Calendar.YEAR) else today.get(Calendar.YEAR) - 1
            "Oct $fyYear - Sep ${fyYear + 1}"
        }
        IITPeriod.PREVIOUS -> {
            val fyYear = if (today.get(Calendar.MONTH) >= Calendar.OCTOBER) today.get(Calendar.YEAR) else today.get(Calendar.YEAR) - 1
            "Before Oct $fyYear"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IITScreen(
    navController: NavController,
    viewModel: IITViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    fun isExpanded(p: IITPeriod) = expanded[p.name] ?: true
    fun toggleExpand(p: IITPeriod) {
        expanded[p.name] = !isExpanded(p)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "IIT Clients",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Interruption in Treatment",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = {
                            Text(
                                "Search by name or hospital number...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(
                                        Icons.Default.Clear,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = Color.White,
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
                        Text("Loading IIT clients...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
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
                                Icon(
                                    Icons.Default.Info,
                                    null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                )
                                Text(
                                    "IIT (Interruption in Treatment): clients on ART who have not returned for a scheduled refill for >= 28 consecutive days (PEPFAR/NACA definition). Clients are grouped by when they entered IIT status.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5D4037)
                                )
                            }
                        }
                    }

                    item { IITPeriodSummaryRow(uiState) }

                    if (uiState.searchQuery.isNotBlank()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${uiState.clients.size} result${if (uiState.clients.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IITPeriod.values().forEach { period ->
                        val periodClients = uiState.groupedByPeriod[period] ?: emptyList()
                        item(key = "header_${period.name}") {
                            IITPeriodSectionHeader(
                                period = period,
                                count = periodClients.size,
                                expanded = isExpanded(period),
                                onToggle = { toggleExpand(period) }
                            )
                        }
                        if (isExpanded(period)) {
                            if (periodClients.isEmpty()) {
                                item(key = "empty_${period.name}") {
                                    IITEmptyPeriodRow(period)
                                }
                            } else {
                                itemsIndexed(periodClients, key = { _, client -> client.patientId }) { index, client ->
                                    IITClientRow(
                                        client = client,
                                        alternate = index % 2 == 1,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        IITGrandTotalCard(
                            total = uiState.totalCount,
                            early = uiState.earlyIITCount,
                            iit = uiState.iitCount,
                            ltfu = uiState.ltfuCount,
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
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun IITEmptyPeriodRow(period: IITPeriod) {
    Text(
        text = "No clients in ${period.label}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun IITPeriodSummaryRow(state: IITUiState) {
    val periodCounts = listOf(
        IITPeriod.TODAY to state.todayCount,
        IITPeriod.THIS_WEEK to state.thisWeekCount,
        IITPeriod.LAST_WEEK to state.lastWeekCount,
        IITPeriod.THIS_MONTH to state.thisMonthCount,
        IITPeriod.THIS_FY to state.thisFYCount,
        IITPeriod.PREVIOUS to state.previousCount
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(periodCounts) { (period, count) ->
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
                    Text(
                        period.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun IITPeriodSectionHeader(
    period: IITPeriod,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val fg = periodColor(period)
    val bg = periodBg(period)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(fg)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        period.label,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = fg
                    )
                    Text(
                        periodDateRange(period),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$count client${if (count == 1) "" else "s"}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = fg
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun IITGrandTotalCard(
    total: Int,
    early: Int,
    iit: Int,
    ltfu: Int,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.elevatedCardElevation(3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.People,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Grand Total IIT",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "$total",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrandTierChip("Early IIT\n28-59d", early, TierEarlyFg, TierEarlyBg, Modifier.weight(1f))
                GrandTierChip("IIT\n60-179d", iit, TierIITFg, TierIITBg, Modifier.weight(1f))
                GrandTierChip("LTFU\n180+d", ltfu, TierLTFUFg, Color(0xFFF9EBEB), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GrandTierChip(label: String, count: Int, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = fg)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.85f),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun IITEmptyState(searchQuery: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = Color(0xFF2E7D32))
            Spacer(Modifier.height(12.dp))
            Text(
                if (searchQuery.isBlank()) "No IIT clients found" else "No IIT clients match \"$searchQuery\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (searchQuery.isBlank()) "All active clients are within their scheduled refill dates." else "Try a different name or hospital number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IITClientRow(client: IITClient, alternate: Boolean = false, modifier: Modifier = Modifier) {
    val bg = tierBg(client.iitTier)
    val fg = tierFg(client.iitTier)
    val av = avatarBg(client.iitTier)
    val rowBg = if (alternate) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surface

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = rowBg)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(av)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(CircleShape).background(av)) {
                    Text("${client.initial}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                client.hospitalNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp
                            )
                        }
                        client.sex?.takeIf { it.isNotBlank() }?.let { sex ->
                            val sexBg = if (sex.lowercase().startsWith("m")) Color(0xFFE3F2FD) else Color(0xFFFCE4EC)
                            val sexFg = if (sex.lowercase().startsWith("m")) Color(0xFF1565C0) else Color(0xFFC2185B)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(sexBg)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    sex.uppercase().take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sexFg,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Last visit: ${DateUtils.formatDate(client.lastVisitDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Missed appt: ${DateUtils.formatDate(client.nextAppointment)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = fg
                    )
                    client.dsdModel?.takeIf { it.isNotBlank() }?.let { dsd ->
                        Text(
                            text = "DSD: $dsd",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "${client.daysOverdue}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = fg
                            )
                            Text("days", fontSize = 9.sp, color = fg.copy(alpha = 0.8f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(av)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(client.iitTier.shortLabel, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}
