package com.carecompanion.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary              = ClinicalBlue900,
    onPrimary            = White,
    primaryContainer     = ClinicalBlue100,
    onPrimaryContainer   = ClinicalBlue900,
    secondary            = HIVRed,
    onSecondary          = White,
    secondaryContainer   = HIVRedLight,
    onSecondaryContainer = HIVRedDark,
    tertiary             = MedicalGreen,
    onTertiary           = White,
    tertiaryContainer    = MedicalGreenLight,
    onTertiaryContainer  = MedicalGreen,
    error                = HIVRed,
    onError              = White,
    errorContainer       = HIVRedLight,
    onErrorContainer     = HIVRedDark,
    background           = ClinicalSurface,
    onBackground         = ClinicalGray900,
    surface              = White,
    onSurface            = ClinicalGray900,
    surfaceVariant       = ClinicalGray100,
    onSurfaceVariant     = ClinicalGray700,
    outline              = ClinicalGray300,
)

private val DarkColorScheme = darkColorScheme(
    primary              = ClinicalBlue100,
    onPrimary            = ClinicalBlue900,
    primaryContainer     = ClinicalBlue800,
    onPrimaryContainer   = White,
    secondary            = HIVRedLight,
    onSecondary          = HIVRedDark,
    secondaryContainer   = HIVRedDark,
    onSecondaryContainer = HIVRedLight,
    tertiary             = MedicalGreenLight,
    onTertiary           = MedicalGreen,
    background           = ClinicalGray900,
    surface              = Color(0xFF1E2D3D),
    surfaceVariant       = Color(0xFF263545),
    onSurfaceVariant     = ClinicalGray300,
)

@Composable
fun CareCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Match the status bar background to the primary (deep clinical blue)
            window.statusBarColor = colorScheme.primary.toArgb()
            // White icons on the dark blue status bar
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = CareCompanionTypography,
        content     = content
    )
}