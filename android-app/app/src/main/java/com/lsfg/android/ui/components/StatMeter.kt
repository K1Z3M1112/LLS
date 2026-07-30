package com.lsfg.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.ui.theme.*

/**
 * Horizontal bar meter with neon fill. Used for FPS, latency, pacing bars.
 */
@Composable
fun StatBar(
    label: String,
    value: Float,          // 0..1
    displayText: String,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    height: Dp = 4.dp,
) {
    val animValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "statBar",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = LsfgTypography.labelMedium)
            Text(
                displayText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = accentColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val trackColor = SpaceBorder
            val y = size.height / 2
            // Track
            drawLine(
                color = trackColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
            // Fill
            if (animValue > 0f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.7f), accentColor)
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width * animValue, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Large monospaced FPS number with label and unit — the centrepiece stat widget.
 */
@Composable
fun BigStatNumber(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = LsfgTypography.labelSmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 38.sp,
            color = valueColor,
            letterSpacing = (-1).sp,
        )
        Text(
            text = unit,
            style = LsfgTypography.labelMedium,
            color = TextSecondary,
        )
    }
}

/** Glowing dot status indicator. */
@Composable
fun StatusDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    val color = if (active) NeonGreen else TextMuted
    Canvas(modifier = modifier.size(size)) {
        drawCircle(color = color.copy(alpha = 0.25f), radius = this.size.width * 0.85f)
        drawCircle(color = color, radius = this.size.width * 0.45f)
    }
}
