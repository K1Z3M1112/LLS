package com.lsfg.android.ui.components

// ── GamingToggle ─────────────────────────────────────────────────────────────
// Ported from DeepDrop, adapted to the LLS orange palette.
// Provides a custom animated toggle switch (GamingSwitch) and a styled slider
// (GamingSlider) — use these inside GamingCard panels for a consistent HUD feel.
// They do NOT replace the Material3 ToggleRow / ValueSlider from LsfgComponents;
// they are additive alternatives for denser/more compact UIs.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lsfg.android.ui.theme.LsfgAccentGlow
import com.lsfg.android.ui.theme.LsfgPrimary

/**
 * Custom gaming-style toggle switch with animated orange fill.
 *
 * Visually matches the "Game Space" toggle aesthetic from DeepDrop while
 * using the LLS orange primary accent.  For accessibility-sensitive contexts
 * prefer the standard [ToggleRow] + Material3 [Switch] instead.
 */
@Composable
fun GamingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue   = if (checked) LsfgPrimary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(200),
        label         = "trackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue   = if (checked) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label         = "thumbColor",
    )
    val borderColor = if (checked) LsfgAccentGlow.copy(0.5f) else MaterialTheme.colorScheme.outlineVariant
    val thumbOffset = if (checked) 20.dp else 2.dp
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                enabled           = enabled,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .align(Alignment.CenterStart)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/**
 * Material3 slider styled with the LLS orange accent.
 * Drop-in replacement for bare Slider calls inside [GamingCard] panels.
 */
@Composable
fun GamingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Slider(
        value         = value,
        onValueChange = onValueChange,
        modifier      = modifier,
        valueRange    = valueRange,
        steps         = steps,
        enabled       = enabled,
        colors        = SliderDefaults.colors(
            thumbColor          = LsfgPrimary,
            activeTrackColor    = LsfgPrimary,
            inactiveTrackColor  = MaterialTheme.colorScheme.surfaceContainerHighest,
            activeTickColor     = Color.Transparent,
            inactiveTickColor   = Color.Transparent,
        ),
    )
}
