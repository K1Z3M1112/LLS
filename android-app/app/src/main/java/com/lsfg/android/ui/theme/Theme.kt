package com.lsfg.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LsfgColorScheme = darkColorScheme(
    primary          = RedCore,
    onPrimary        = TextPrimary,
    primaryContainer = RedAlpha30,
    onPrimaryContainer = TextPrimary,

    secondary        = NeonCyan,
    onSecondary      = SpaceBlack,
    secondaryContainer = Color(0xFF003344),
    onSecondaryContainer = NeonCyan,

    tertiary         = NeonOrange,
    onTertiary       = SpaceBlack,
    tertiaryContainer = Color(0xFF3D2010),
    onTertiaryContainer = NeonOrange,

    background       = SpaceBlack,
    onBackground     = TextPrimary,
    surface          = SpaceNavy,
    onSurface        = TextPrimary,
    surfaceVariant   = SpaceSurface,
    onSurfaceVariant = TextSecondary,

    outline          = SpaceBorder,
    outlineVariant   = Color(0xFF0A0A18),

    error            = ErrorRed,
    onError          = TextPrimary,
    errorContainer   = Color(0xFF4D0010),
    onErrorContainer = ErrorRed,

    inverseSurface   = TextPrimary,
    inverseOnSurface = SpaceBlack,
    inversePrimary   = RedDeep,

    scrim            = Color(0xCC000000),
    surfaceTint      = RedAlpha15,
)

@Composable
fun LsfgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LsfgColorScheme,
        typography  = LsfgTypography,
        content     = content,
    )
}
