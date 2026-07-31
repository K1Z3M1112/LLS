package com.lsfg.android.ui.components

// ── GamingCard ────────────────────────────────────────────────────────────────
// Ported from DeepDrop, adapted to the LLS orange-on-dark palette.
// Provides a glowing-border card aesthetic (RedMagic / ASUS ROG-style) that
// can be used anywhere in the app as a drop-in alongside LsfgCard.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lsfg.android.ui.theme.LsfgPrimary

val GamingCardShape      = RoundedCornerShape(12.dp)
val GamingCardShapeSmall = RoundedCornerShape(8.dp)

/**
 * Gaming-style card with a subtle glow border and dark gradient fill.
 *
 * Drop-in complement to [LsfgCard]: use this for active-session panels,
 * stats, and control groups that should feel more "game HUD".
 *
 * @param glowColor  Border/glow colour — defaults to the app primary (orange).
 * @param glowAlpha  Border opacity; keep ≤ 0.5 to stay subtle.
 */
@Composable
fun GamingCard(
    modifier: Modifier = Modifier,
    glowColor: Color = LsfgPrimary,
    glowAlpha: Float = 0.35f,
    borderWidth: Dp = 1.dp,
    innerPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = glowColor.copy(alpha = glowAlpha)
    val surfaceContainer   = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHi = MaterialTheme.colorScheme.surfaceContainerHigh

    val fillBrush = Brush.verticalGradient(
        listOf(surfaceContainer.copy(alpha = 0.95f), surfaceContainerHi.copy(alpha = 0.85f))
    )

    Column(
        modifier = modifier
            .clip(GamingCardShape)
            .background(fillBrush)
            .border(borderWidth, borderColor, GamingCardShape)
            .drawBehind {
                // Soft outer glow — slightly-larger rounded rect at very low alpha.
                val expand = 6.dp.toPx()
                drawRoundRect(
                    color = glowColor.copy(alpha = 0.08f),
                    topLeft = Offset(-expand, -expand),
                    size = size.copy(
                        width  = size.width  + expand * 2,
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

/**
 * Accent-tinted stat card. Thin border with configurable accent colour.
 * Use [LsfgStatusGood] / [LsfgStatusWarn] / [LsfgStatusBad] for context-aware tints.
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    accentColor: Color = LsfgPrimary,
    innerPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) = GamingCard(
    modifier      = modifier,
    glowColor     = accentColor,
    glowAlpha     = 0.40f,
    borderWidth   = 1.dp,
    innerPadding  = innerPadding,
    content       = content,
)
