package com.lsfg.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lsfg.android.ui.theme.*

val CardShape = RoundedCornerShape(12.dp)
val CardShapeSmall = RoundedCornerShape(8.dp)

/**
 * Base gaming-style card with a subtle red glow border.
 * Mimics the RedMagic Game Space panel aesthetic.
 */
@Composable
fun GamingCard(
    modifier: Modifier = Modifier,
    glowColor: Color = RedCore,
    glowAlpha: Float = 0.35f,
    borderWidth: Dp = 1.dp,
    innerPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = glowColor.copy(alpha = glowAlpha)
    val fillBrush = Brush.verticalGradient(
        listOf(SpaceSurface.copy(alpha = 0.95f), SpaceSurface2.copy(alpha = 0.85f))
    )

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(fillBrush)
            .border(borderWidth, borderColor, CardShape)
            .drawBehind {
                // Soft outer glow — draw a slightly larger rounded rect in the
                // glow colour at very low alpha to simulate bloom.
                val expand = 6.dp.toPx()
                drawRoundRect(
                    color = glowColor.copy(alpha = 0.08f),
                    topLeft = androidx.compose.ui.geometry.Offset(-expand, -expand),
                    size = size.copy(
                        width = size.width + expand * 2,
                        height = size.height + expand * 2,
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx() + expand),
                    style = Stroke(width = expand * 2),
                )
            }
            .padding(innerPadding),
        content = content,
    )
}

/** Accent-coloured card (cyan for stats, orange for warnings). */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    innerPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) = GamingCard(
    modifier = modifier,
    glowColor = accentColor,
    glowAlpha = 0.40f,
    borderWidth = 1.dp,
    innerPadding = innerPadding,
    content = content,
)
