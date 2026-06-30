package com.carecompanion.presentation.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.carecompanion.data.database.entities.InfantRecord
import com.carecompanion.presentation.viewmodels.EidWorklistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EidWorklistScreen(
    navController: NavController,
    viewModel: EidWorklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EID — Exposed-Infant Diagnosis") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.total} exposed infant(s) · ${state.highRisk} high-risk · ${state.withGap} with a gap",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Cascade: ARV at birth → EID PCR at 6 wk → CTX → 18-mo antibody.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally,
                ) { Text("No exposed infants on record.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items) { InfantCard(it) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun InfantCard(r: InfantRecord) {
    val accent = when (r.gapSeverity) {
        "critical" -> Color(0xFFC62828)
        "high" -> Color(0xFFE65100)
        else -> Color(0xFF2E7D32)
    }
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.name ?: "Infant", fontWeight = FontWeight.Bold)
                Text(
                    "${r.ageWeeks ?: "?"} wk",
                    color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.highRisk) Badge("HIGH-RISK", Color(0xFFAD1457))
                if (r.arvGiven) Badge("ARV", Color(0xFF2E7D32))
                if (r.pcrDone) Badge(if (r.pcrPositive) "PCR+" else "PCR", if (r.pcrPositive) Color(0xFFC62828) else Color(0xFF2E7D32))
                if (r.ctxGiven) Badge("CTX", Color(0xFF2E7D32))
            }
            if (r.gapMessage != null) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.12f)) {
                    Text(
                        if (r.gapCount > 1) "${r.gapMessage}  (+${r.gapCount - 1} more)" else r.gapMessage,
                        color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
            if (r.highRisk && r.highRiskReason != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    r.highRiskReason + if (!r.finalResultKnown) " · high-risk until 18-mo result" else "",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!r.interventionsSummary.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Given: ${r.interventionsSummary}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
