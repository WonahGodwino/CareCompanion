package com.carecompanion.presentation.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carecompanion.presentation.navigation.Screen

// ─────────────────────────────────────────────────────────────────────────────
// Service module data model
// ─────────────────────────────────────────────────────────────────────────────
private data class ServiceModule(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onAccentColor: Color,
    val available: Boolean = false,
    val route: String? = null        // non-null = live screen; null = coming soon
)

private fun serviceModules() = listOf(
    // ── LIVE ──────────────────────────────────────────────────────────────────
    ServiceModule(
        title         = "IIT — Interruption in Treatment",
        description   = "Clients on ART who have not returned for a scheduled refill for ≥ 28 consecutive days (PEPFAR definition). Immediate follow-up required.",
        icon          = Icons.Default.PersonOff,
        accentColor   = Color(0xFFC62828),
        onAccentColor = Color(0xFFFFEBEE),
        available     = true,
        route         = Screen.IIT.route
    ),
    // ── COMING SOON ───────────────────────────────────────────────────────────
    ServiceModule(
        title         = "ART Refills",
        description   = "Track upcoming and overdue antiretroviral therapy refill due dates for active clients.",
        icon          = Icons.Default.Medication,
        accentColor   = Color(0xFF01579B),
        onAccentColor = Color(0xFFE3F2FD),
    ),
    ServiceModule(
        title         = "Missed Appointments",
        description   = "Identify clients who missed scheduled clinic appointments and require follow-up.",
        icon          = Icons.Default.EventBusy,
        accentColor   = Color(0xFFE65100),
        onAccentColor = Color(0xFFFFF3E0),
    ),
    ServiceModule(
        title         = "Viral Load",
        description   = "Monitor viral load suppression status and flag clients due for VL sample collection.",
        icon          = Icons.Default.Biotech,
        accentColor   = Color(0xFF6A1B9A),
        onAccentColor = Color(0xFFF3E5F5),
    ),
    ServiceModule(
        title         = "TPT",
        description   = "Manage Tuberculosis Preventive Therapy eligibility, initiation, and completion tracking.",
        icon          = Icons.Default.HealthAndSafety,
        accentColor   = Color(0xFF00695C),
        onAccentColor = Color(0xFFE0F2F1),
    ),
    ServiceModule(
        title         = "TB",
        description   = "Track tuberculosis screening, presumptive TB identification, and treatment follow-up workflows.",
        icon          = Icons.Default.MedicalServices,
        accentColor   = Color(0xFF2E7D32),
        onAccentColor = Color(0xFFE8F5E9),
    ),
    ServiceModule(
        title         = "AHD",
        description   = "Identify and manage clients with Advanced HIV Disease for prioritised clinical care.",
        icon          = Icons.Default.MonitorHeart,
        accentColor   = Color(0xFF4A148C),
        onAccentColor = Color(0xFFF3E5F5),
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(navController: NavController) {
    val modules = serviceModules()
    val liveModules    = modules.filter { it.available }
    val comingSoon     = modules.filter { !it.available }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Services",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Clinical Care Management Modules",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Active modules section ───────────────────────────────────────
            if (liveModules.isNotEmpty()) {
                item {
                    Text(
                        "Active",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color(0xFF2E7D32),
                        modifier   = Modifier.padding(bottom = 2.dp)
                    )
                }
                items(liveModules) { module ->
                    ServiceCard(module = module, onClick = {
                        module.route?.let { navController.navigate(it) }
                    })
                }
            }

            // ── Coming soon section ─────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Coming Soon",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "— modules activate as full ART data is integrated",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            items(comingSoon) { module ->
                ServiceCard(module = module, onClick = null)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual service card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ServiceCard(module: ServiceModule, onClick: (() -> Unit)?) {
    val cardModifier = if (onClick != null)
        Modifier.fillMaxWidth().clickable { onClick() }
    else
        Modifier.fillMaxWidth()

    ElevatedCard(
        modifier  = cardModifier,
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (module.available) 4.dp else 2.dp),
        colors    = CardDefaults.elevatedCardColors(
            containerColor = if (module.available)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {

            // ── Left accent bar ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(module.accentColor)
            )

            // ── Main card content ────────────────────────────────────────────
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {

                // Header row: icon + title + trailing chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon badge — light bg / tinted icon (more refined than white-on-full-color)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(module.onAccentColor)
                    ) {
                        Icon(
                            imageVector        = module.icon,
                            contentDescription = null,
                            tint               = module.accentColor,
                            modifier           = Modifier.size(24.dp)
                        )
                    }

                    // Title + status badge
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = module.title,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        if (module.available) {
                            // Green "Active" badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text       = "● Active",
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Color(0xFF2E7D32),
                                    letterSpacing = 0.3.sp
                                )
                            }
                        } else {
                            // Outlined "Coming Soon" chip in the module's accent colour
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(
                                        width = 1.dp,
                                        color = module.accentColor.copy(alpha = 0.60f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .background(module.onAccentColor)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text      = "Coming Soon",
                                    fontSize  = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color     = module.accentColor,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }

                    // Trailing chevron — full opacity for active, grayed for coming soon
                    Icon(
                        imageVector        = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint               = if (module.available)
                            module.accentColor
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier           = Modifier.size(22.dp)
                    )
                }

                // ── Divider ─────────────────────────────────────────────────
                HorizontalDivider(
                    modifier  = Modifier.padding(top = 12.dp, bottom = 10.dp),
                    thickness = 0.8.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Description
                Text(
                    text  = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
