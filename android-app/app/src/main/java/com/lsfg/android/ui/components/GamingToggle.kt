package com.lsfg.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lsfg.android.ui.theme.*

/** Custom gaming-style toggle switch with RedMagic red fill. */
@Composable
fun GamingSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) RedCore else SpaceSurface2,
        animationSpec = tween(200),
        label = "trackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) TextPrimary else TextDisabled,
        animationSpec = tween(200),
        label = "thumbColor",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val thumbOffset = if (checked) 20.dp else 2.dp

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .border(
                1.dp,
                if (checked) RedGlow.copy(0.5f) else SpaceBorder,
                RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
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

/** Slider using Material3 under a red custom track. */
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
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = RedCore,
            activeTrackColor = RedCore,
            inactiveTrackColor = SpaceBorder,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}
