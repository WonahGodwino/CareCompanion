package com.carecompanion.presentation.ui.services

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.presentation.viewmodels.VlCascadeUiState
import com.carecompanion.presentation.viewmodels.VlCascadeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlCascadeScreen(
    navController: NavController,
    viewModel: VlCascadeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Viral Load Cascade", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge)
                        Text("PEPFAR 95-95-95 Tracking",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A1B9A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF6A1B9A))
            }
        } else {
            CascadeContent(state = state, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun CascadeContent(state: VlCascadeUiState, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PEPFAR 95-95-95 explanation
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = Color(0xFF6A1B9A), modifier = Modifier.size(20.dp))
                Text(
                    "PEPFAR 95-95-95 targets: 95% of PLHIV know status · 95% of those on ART · 95% of those virally suppressed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4A148C), lineHeight = 18.sp
                )
            }
        }

        // Cascade waterfall
        CascadeStep(
            label     = "TX_CURR (Active on ART)",
            count     = state.txCurr,
            total     = state.txCurr,
            pct       = 100f,
            barColor  = Color(0xFF1565C0),
            isFirst   = true
        )
        CascadeStep(
            label    = "VL Sample Collected",
            count    = state.vlTested,
            total    = state.txCurr,
            pct      = state.testedPct,
            barColor = Color(0xFF6A1B9A)
        )
        CascadeStep(
            label    = "VL Result Received",
            count    = state.vlResultReceived,
            total    = state.txCurr,
            pct      = state.resultReceivedPct,
            barColor = Color(0xFF00695C)
        )

        // Suppressed / Unsuppressed split
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CascadeOutcomeCard(
                label    = "Virally Suppressed",
                subtitle = "< 1,000 copies/mL",
                count    = state.vlSuppressed,
                pct      = state.suppressionAmongTested,
                color    = Color(0xFF2E7D32),
                bgColor  = Color(0xFFE8F5E9),
                modifier = Modifier.weight(1f)
            )
            CascadeOutcomeCard(
                label    = "Unsuppressed",
                subtitle = "≥ 1,000 copies/mL",
                count    = state.vlUnsuppressed,
                pct      = if (state.vlResultReceived > 0)
                    state.vlUnsuppressed * 100f / state.vlResultReceived else 0f,
                color    = Color(0xFFC62828),
                bgColor  = Color(0xFFFFEBEE),
                modifier = Modifier.weight(1f)
            )
        }

        // Suppression rate highlight
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (state.suppressionAmongTested >= 95f) Color(0xFFE8F5E9)
                else Color(0xFFFFF8E1)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Suppression Rate", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    Icon(
                        if (state.suppressionAmongTested >= 95f) Icons.Default.CheckCircle
                        else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (state.suppressionAmongTested >= 95f) Color(0xFF2E7D32)
                        else Color(0xFFF57F17),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("${"%.1f".format(state.suppressionAmongTested)}% of patients with results are suppressed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (state.suppressionAmongTested >= 95f) "✓ Meeting PEPFAR 95% suppression target"
                    else "Below PEPFAR 95% suppression target — review unsuppressed clients",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.suppressionAmongTested >= 95f) Color(0xFF2E7D32) else Color(0xFFE65100),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CascadeStep(
    label: String, count: Int, total: Int,
    pct: Float, barColor: Color, isFirst: Boolean = false
) {
    ElevatedCard(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text("%,d".format(count), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = barColor)
                    if (!isFirst) {
                        Text("${"%.1f".format(pct)}% of TX_CURR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!isFirst) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun CascadeOutcomeCard(
    label: String, subtitle: String, count: Int, pct: Float,
    color: Color, bgColor: Color, modifier: Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.70f))
            Spacer(Modifier.height(8.dp))
            Text("%,d".format(count), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold, color = color)
            Text("${"%.1f".format(pct)}%", style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, color = color.copy(alpha = 0.80f))
        }
    }
}
