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
import com.carecompanion.presentation.viewmodels.PmtctWorklistItem
import com.carecompanion.presentation.viewmodels.PmtctWorklistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmtctWorklistScreen(
    navController: NavController,
    viewModel: PmtctWorklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PMTCT — Maternal VL") },
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
                "${state.total} pregnant client(s) · ${state.withGap} needing a PMTCT VL",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "PMTCT VL is collected at 32–36 weeks gestation to assess MTCT risk.",
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
                ) { Text("No currently-pregnant clients on record.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items) { PmtctCard(it) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PmtctCard(item: PmtctWorklistItem) {
    val r = item.record
    val accent = when (r.gapSeverity) {
        "critical" -> Color(0xFFC62828)
        "high" -> Color(0xFFE65100)
        else -> Color(0xFF2E7D32)   // no gap (VL done / not yet at window)
    }
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.name ?: "Unknown", fontWeight = FontWeight.Bold)
                Text(
                    "GA ${item.currentGaWeeks ?: "?"} wk",
                    color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
            }
            r.hospitalNumber?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (r.fetalHighRisk) {
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFAD1457).copy(alpha = 0.12f)) {
                    Text(
                        "FETUS HIGH-RISK — prepare enhanced infant prophylaxis at birth" +
                            (r.fetalHighRiskReason?.let { " ($it)" } ?: ""),
                        color = Color(0xFFAD1457), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (r.gapMessage != null) {
                Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.12f)) {
                    Text(
                        r.gapMessage,
                        color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            } else {
                Text(
                    if (r.pmtctVlDone) "PMTCT VL done ✓" else "Not yet at the 32–36 wk window",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
