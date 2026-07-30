@file:Suppress("DEPRECATION", "DEPRECATION_ERROR") // rememberRipple() is deprecated at
// ERROR level in this BOM's material-ripple version (a separate diagnostic ID,
// DEPRECATION_ERROR, from the plain DEPRECATION warning -- suppressing only
// "DEPRECATION" doesn't silence it). Its replacement `ripple()` isn't available yet in
// the compose-bom 2024.09.03 pin (unresolved reference when tried) -- suppress both
// until the BOM is bumped past whatever patch actually ships ripple().

package com.lsfg.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
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
import com.lsfg.android.ui.theme.*

/** Large, primary red gradient action button with neon glow. */
@Composable
fun NeonButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = if (enabled)
        Brush.horizontalGradient(listOf(GradientRedStart, GradientRedEnd))
    else
        Brush.horizontalGradient(listOf(SpaceSurface2, SpaceSurface2))

    val borderColor = if (enabled) RedGlow else SpaceBorder
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = Color.White),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.2.sp,
            color = if (enabled) TextPrimary else TextDisabled,
        )
    }
}

/** Outlined ghost button — used for secondary actions. */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = RedCore,
    icon: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveColor = if (enabled) accentColor else TextDisabled
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(effectiveColor.copy(alpha = 0.10f))
            .border(1.dp, effectiveColor.copy(alpha = 0.50f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = effectiveColor),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label.uppercase(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.0.sp,
            color = effectiveColor,
        )
    }
}

/** Pill-shaped multiplier selector button. */
@Composable
fun MultiplierChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (selected) Brush.horizontalGradient(listOf(RedCore, RedDeep))
             else Brush.horizontalGradient(listOf(SpaceSurface, SpaceSurface))
    val borderColor = if (selected) RedGlow else SpaceBorder
    val textColor = if (selected) TextPrimary else TextSecondary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = RedCore),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.5.sp,
            color = textColor,
        )
    }
}
