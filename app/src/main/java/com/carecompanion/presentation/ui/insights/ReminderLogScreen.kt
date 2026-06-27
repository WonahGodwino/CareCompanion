package com.carecompanion.presentation.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.viewmodels.ReminderLogRow
import com.carecompanion.presentation.viewmodels.ReminderLogViewModel
import com.carecompanion.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderLogScreen(
    navController: NavController,
    viewModel: ReminderLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder Audit", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear log")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4A148C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatChip("Reminders sent", "${state.successCount}", "of ${state.totalAttempts} attempts",
                    Color(0xFF1565C0), Modifier.weight(1f))
                StatChip("Delivery rate", "${state.successRate}%", "gateway accepted",
                    Color(0xFF00838F), Modifier.weight(1f))
                StatChip("Attendance", "${state.attendanceRate}%", "of ${state.measurable} measurable",
                    Color(0xFF2E7D32), Modifier.weight(1f))
            }
            Text("Attendance = a reminded client who later had a pharmacy visit on/after their appointment date. " +
                "Only reminders whose appointment has passed are counted.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.rows.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No reminders sent yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.rows, key = { it.log.id }) { row -> LogRow(row) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LogRow(row: ReminderLogRow) {
    val log = row.log
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Outcome icon
            val (icon, tint) = when (row.attended) {
                true  -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
                false -> Icons.Default.Error to Color(0xFFC62828)
                null  -> Icons.Default.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(log.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("#${log.hospitalNumber} · ${DateUtils.formatDateTime(log.sentAt)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val delivery = if (log.success) "Sent via ${log.channels}" else "Failed: ${log.detail ?: "unknown"}"
                Text("$delivery · ${if (log.auto) "auto" else "manual"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.success) MaterialTheme.colorScheme.onSurface else Color(0xFFC62828))
            }
            Box(modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF4A148C).copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("${log.riskBand} ${log.riskScore}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C))
            }
        }
    }
}
