package com.carecompanion.presentation.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.navigation.Screen
import com.carecompanion.presentation.viewmodels.AiInsightsViewModel
import com.carecompanion.utils.PatientRiskEngine.PatientRiskScore
import com.carecompanion.utils.PatientRiskEngine.RiskFactor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(
    navController: NavController,
    viewModel: AiInsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val reminderEvent by viewModel.reminderEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reminderEvent) {
        reminderEvent?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearReminderEvent()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AI Patient Monitoring", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Predictive IIT risk · on-device · explainable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ModelValidation.route) }) {
                        Icon(Icons.Default.Insights, contentDescription = "Model validation")
                    }
                    IconButton(onClick = { navController.navigate(Screen.ReminderLog.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Reminder audit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = Color(0xFF4A148C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Risk summary row ───────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryChip("Critical", state.criticalCount, Color(0xFFB71C1C), Modifier.weight(1f))
                SummaryChip("High",     state.highCount,     Color(0xFFE65100), Modifier.weight(1f))
                SummaryChip("Moderate", state.moderateCount, Color(0xFFF9A825), Modifier.weight(1f))
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.totalFlagged == 0 -> EmptyState()
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    section(
                        title = "Forecast — prevent the miss",
                        subtitle = "Upcoming appointments predicted to be missed, from adherence pattern & access.",
                        scores = state.forecast,
                        navController = navController,
                        onSendReminder = viewModel::sendReminder,
                    )
                    section(
                        title = "Approaching IIT — act now",
                        subtitle = "1–27 days late. Each becomes IIT on day 28 if not traced.",
                        scores = state.approaching,
                        navController = navController,
                        onSendReminder = viewModel::sendReminder,
                    )
                    section(
                        title = "Established IIT",
                        subtitle = "Already interrupted (>28 days). Return-to-treatment tracing.",
                        scores = state.established,
                        navController = navController,
                        onSendReminder = viewModel::sendReminder,
                    )
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    subtitle: String,
    scores: List<PatientRiskScore>,
    navController: NavController,
    onSendReminder: (String) -> Unit,
) {
    if (scores.isEmpty()) return
    item(key = "header-$title") {
        Column(Modifier.padding(top = 4.dp)) {
            Text("$title  (${scores.size})", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(scores, key = { it.uuid }) { score ->
        RiskCard(
            score = score,
            onOpenProfile = { navController.navigate(Screen.PatientProfile.createRoute(score.uuid)) },
            onSendReminder = { onSendReminder(score.uuid) },
        )
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$count", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun RiskCard(score: PatientRiskScore, onOpenProfile: () -> Unit, onSendReminder: () -> Unit) {
    val bandColor = Color(score.band.colorHex)
    val subtitle = when {
        score.isForecast        -> "#${score.hospitalNumber} · appointment in ${score.daysUntilAppointment}d"
        score.isApproachingIit  -> "#${score.hospitalNumber} · ${score.daysUntilIit}d to IIT · ${score.daysOverdue}d late"
        else                    -> "#${score.hospitalNumber} · ${score.daysOverdue}d overdue"
    }
    Card(
        onClick = onOpenProfile,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Risk score badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(bandColor.copy(alpha = 0.15f))
                ) {
                    Text("${score.score}", fontWeight = FontWeight.ExtraBold, color = bandColor,
                        style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(score.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Band pill
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bandColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(score.band.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Explainability — the "why"
            if (score.factors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    score.factors.take(4).forEach { FactorRow(it) }
                }
            }

            // Recommended action
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bandColor.copy(alpha = 0.08f))
                    .padding(10.dp)
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = bandColor,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(score.recommendedAction, style = MaterialTheme.typography.bodySmall)
            }

            // Reminder action (SMS/Email via configured gateway)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onSendReminder) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send reminder")
                }
            }
        }
    }
}

@Composable
private fun FactorRow(factor: RiskFactor) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(Modifier.width(8.dp))
        Text(factor.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("+${factor.points}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text("No clients at adherence risk", color = Color.White, fontWeight = FontWeight.Bold)
            Text("All monitored clients are currently retained in care.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
