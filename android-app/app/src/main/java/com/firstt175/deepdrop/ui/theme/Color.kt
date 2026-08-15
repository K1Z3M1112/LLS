package com.firstt175.deepdrop.ui.theme

import androidx.compose.ui.graphics.Color

// LSFG-Android+ palette — warm charcoal base ("phosphor amber" retro-terminal
// accent) with a cool teal secondary accent for contrast. Dark, high-contrast,
// and readable first; the retro cues live in the accent hues and monospace
// labels (see Type.kt), not in low-contrast novelty colors.
val LsfgPrimary = Color(0xFFE3A857)
val LsfgOnPrimary = Color(0xFF35230A)
val LsfgPrimaryContainer = Color(0xFF4A341A)
val LsfgOnPrimaryContainer = Color(0xFFF6E2C0)

val LsfgSecondary = Color(0xFFC2B9AE)
val LsfgOnSecondary = Color(0xFF2E2A24)
val LsfgSecondaryContainer = Color(0xFF423C33)
val LsfgOnSecondaryContainer = Color(0xFFE9E2D8)

// Cool teal — the "CRT glow" counterpoint to the amber primary. Used sparingly
// for secondary accents so the two hues read as an intentional duotone.
val LsfgTertiary = Color(0xFF7FB8B5)
val LsfgOnTertiary = Color(0xFF0B2C2A)
val LsfgTertiaryContainer = Color(0xFF1E3E3B)
val LsfgOnTertiaryContainer = Color(0xFFD6EDEB)

val LsfgError = Color(0xFFE0958D)
val LsfgOnError = Color(0xFF3A0A06)
val LsfgErrorContainer = Color(0xFF5C231D)
val LsfgOnErrorContainer = Color(0xFFF4D8D4)

val LsfgBackground = Color(0xFF14120E)
val LsfgOnBackground = Color(0xFFEDE7DE)

val LsfgSurface = Color(0xFF17150F)
val LsfgOnSurface = Color(0xFFEDE7DE)
val LsfgOnSurfaceVariant = Color(0xFFB3AB9E)

val LsfgSurfaceDim = Color(0xFF14120E)
val LsfgSurfaceBright = Color(0xFF34302A)
val LsfgSurfaceContainerLowest = Color(0xFF0D0C09)
val LsfgSurfaceContainerLow = Color(0xFF1A1811)
val LsfgSurfaceContainer = Color(0xFF201E16)
val LsfgSurfaceContainerHigh = Color(0xFF2A271D)
val LsfgSurfaceContainerHighest = Color(0xFF363228)

val LsfgOutline = Color(0xFF463F30)
val LsfgOutlineVariant = Color(0xFF2A271D)

// Status hues stay in their own green/orange/red families so they never get
// confused with the amber primary accent, even though all are "warm".
val LsfgStatusGood = Color(0xFF89C29A)
val LsfgStatusWarn = Color(0xFFDD8C4A)
val LsfgStatusBad = Color(0xFFCF7D72)
