package com.carecompanion.presentation.ui.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.carecompanion.data.risk.ClassMetrics
import com.carecompanion.data.risk.RiskScoringMode
import com.carecompanion.presentation.viewmodels.ModelValidationViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelValidationScreen(
    navController: NavController,
    viewModel: ModelValidationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Validation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4A148C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Backtests the AI against this facility's own pickup history with 5-fold " +
                "cross-validation (split by patient — no leakage): for each past appointment we " +
                "already know whether the client became IIT, so we can measure how well the score " +
                "predicts it — and compare a data-learned model on the same data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Active scoring mode banner
            val learned = state.activeMode == RiskScoringMode.LEARNED
            Card(colors = CardDefaults.cardColors(
                containerColor = (if (learned) Color(0xFF1565C0) else Color(0xFF4A148C)).copy(alpha = 0.10f))) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Live scoring: ${if (learned) "Learned model" else "Heuristic engine"}",
                        fontWeight = FontWeight.SemiBold)
                    if (learned) TextButton(onClick = { viewModel.revertToHeuristic() }) { Text("Revert") }
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.runValidation() }, enabled = !state.running, modifier = Modifier.weight(1f)) {
                    if (state.running) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp)); Text("Running…")
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Run validation")
                    }
                }
                OutlinedButton(onClick = { viewModel.pullFromServer() }, enabled = !state.running, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Pull from WINCO")
                }
            }

            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                    Text(it, Modifier.padding(12.dp), color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            state.result?.let { r ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Dataset", fontWeight = FontWeight.Bold)
                        Text("${r.sampleCount} past appointments from ${r.patientCount} clients",
                            style = MaterialTheme.typography.bodySmall)
                        Text("IIT base rate: ${pct(r.iitRate)} · ${r.folds}-fold cross-validated",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                MetricsCard("Current heuristic engine", r.heuristic, Color(0xFF4A148C))
                MetricsCard("Learned model (logistic regression)", r.learned, Color(0xFF1565C0))

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What the learned model weighted", fontWeight = FontWeight.Bold)
                        Text("Positive = pushes toward IIT risk; magnitude = strength (standardised).",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        r.learnedWeights.forEach { w ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(w.name, style = MaterialTheme.typography.bodySmall)
                                Text((if (w.weight >= 0) "+" else "") + "%.2f".format(w.weight),
                                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                    color = if (w.weight >= 0) Color(0xFFC62828) else Color(0xFF2E7D32))
                            }
                        }
                    }
                }

                // Adoption (guardrail-gated)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.canAdopt) {
                            Text("Guardrail passed", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Learned AUC ${"%.2f".format(r.learned.auc)} ≥ heuristic " +
                                "${"%.2f".format(r.heuristic.auc)} and ≥ 0.60. Safe to adopt for forecast scoring.",
                                style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { viewModel.adopt() },
                                enabled = state.activeMode != RiskScoringMode.LEARNED) {
                                Text(if (state.activeMode == RiskScoringMode.LEARNED) "Adopted" else "Adopt learned model")
                            }
                        } else {
                            Text("Guardrail not met", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Text("The learned model did not beat the heuristic (or AUC < 0.60). " +
                                "Keeping the explainable heuristic — collect more outcome data and re-run.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text("Interpretation: AUC 0.5 = no better than chance, 0.7+ = useful, 0.8+ = strong. " +
                    "Sensitivity is the share of real defaulters the model flags in advance. These are " +
                    "retrospective estimates on local data — confirm prospectively before relying on them.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricsCard(title: String, m: ClassMetrics, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("AUC", "%.2f".format(m.auc), Modifier.weight(1f))
                Metric("Sensitivity", pct(m.sensitivity), Modifier.weight(1f))
                Metric("Specificity", pct(m.specificity), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Precision", pct(m.ppv), Modifier.weight(1f))
                Metric("Accuracy", pct(m.accuracy), Modifier.weight(1f))
                Metric("Defaulters", "${m.positives}/${m.n}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"
