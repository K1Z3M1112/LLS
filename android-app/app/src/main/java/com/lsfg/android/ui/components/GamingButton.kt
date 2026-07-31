package com.lsfg.android.ui.components

// ── GamingButton ──────────────────────────────────────────────────────────────
// Ported from DeepDrop (NeonButton.kt), adapted to the LLS orange palette.
// Provides NeonButton (primary CTA), OutlineButton (secondary), and
// MultiplierChip (selection pill) — all without ripple imports from Material 2.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.ui.theme.LsfgAccentGlow
import com.lsfg.android.ui.theme.LsfgOnPrimary
import com.lsfg.android.ui.theme.LsfgPrimary
import com.lsfg.android.ui.theme.LsfgPrimaryContainer

/** Full-width gradient primary action button with neon glow. */
@Composable
fun NeonButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val shape       = RoundedCornerShape(10.dp)
    val surfaceHi   = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg          = if (enabled)
        Brush.horizontalGradient(listOf(LsfgPrimary, LsfgPrimaryContainer))
    else
        Brush.horizontalGradient(listOf(surfaceHi, surfaceHi))

    val borderColor     = if (enabled) LsfgAccentGlow else MaterialTheme.colorScheme.outlineVariant
    val textColor       = if (enabled) LsfgOnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text         = label.uppercase(),
            fontWeight   = FontWeight.Bold,
            fontSize     = 13.sp,
            letterSpacing = 1.2.sp,
            color        = textColor,
        )
    }
}

/** Outlined ghost button — used for secondary / cancel actions. */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LsfgPrimary,
    icon: @Composable (() -> Unit)? = null,
) {
    val shape             = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(accentColor.copy(alpha = 0.10f))
            .border(1.dp, accentColor.copy(alpha = 0.50f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text          = label.uppercase(),
            fontWeight    = FontWeight.SemiBold,
            fontSize      = 12.sp,
            letterSpacing  = 1.0.sp,
            color         = accentColor,
        )
    }
}

/**
 * Pill-shaped selection chip. Typical use: multiplier (×2 / ×3 / ×4) row.
 * Selected chips get the primary gradient fill; unselected stay on surface.
 */
@Composable
fun MultiplierChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape             = RoundedCornerShape(8.dp)
    val surfaceContainer  = MaterialTheme.colorScheme.surfaceContainer
    val bg = if (selected)
        Brush.horizontalGradient(listOf(LsfgPrimary, LsfgPrimaryContainer))
    else
        Brush.horizontalGradient(listOf(surfaceContainer, surfaceContainer))
    val borderColor = if (selected) LsfgAccentGlow else MaterialTheme.colorScheme.outlineVariant
    val textColor   = if (selected) LsfgOnPrimary  else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = LsfgPrimary),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text          = label,
            fontWeight    = FontWeight.Bold,
            fontSize      = 15.sp,
            letterSpacing  = 0.5.sp,
            color         = textColor,
        )
    }
}
