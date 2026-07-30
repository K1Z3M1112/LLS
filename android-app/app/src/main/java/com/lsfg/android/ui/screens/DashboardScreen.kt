package com.lsfg.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.prefs.LsfgConfig
import com.lsfg.android.ui.components.*
import com.lsfg.android.ui.theme.*

@Composable
fun DashboardScreen(
    config: LsfgConfig,
    isSessionActive: Boolean,
    generatedFps: Double,
    realFps: Double,
    latencyMs: Double,
    postedFps: Double,
    onToggleSession: () -> Unit,
    onConfigChange: (LsfgConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
    ) {
        // ── Header bar ────────────────────────────────────────────────────
        DashboardHeader(isSessionActive = isSessionActive, glowAlpha = glowAlpha)

        Spacer(Modifier.height(16.dp))

        // ── Live FPS stats row ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                accentColor = NeonCyan,
                innerPadding = PaddingValues(12.dp),
            ) {
                BigStatNumber(
                    label = "Posted",
                    value = if (isSessionActive) "%.0f".format(postedFps) else "—",
                    unit = "FPS",
                    valueColor = NeonCyan,
                )
            }
            StatCard(
                modifier = Modifier.weight(1f),
                accentColor = NeonGreen,
                innerPadding = PaddingValues(12.dp),
            ) {
                BigStatNumber(
                    label = "Real",
                    value = if (isSessionActive) "%.0f".format(realFps) else "—",
                    unit = "FPS",
                    valueColor = NeonGreen,
                )
            }
            StatCard(
                modifier = Modifier.weight(1f),
                accentColor = NeonOrange,
                innerPadding = PaddingValues(12.dp),
            ) {
                BigStatNumber(
                    label = "Latency",
                    value = if (isSessionActive) "%.1f".format(latencyMs) else "—",
                    unit = "ms",
                    valueColor = NeonOrange,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Master start / stop button ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            NeonButton(
                label = if (isSessionActive) "Stop Frame Gen" else "Start Frame Gen",
                onClick = onToggleSession,
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Frame Gen settings ─────────────────────────────────────────
        SectionHeader(
            title = "Frame Generation",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))

        GamingCard(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)) {

            // Multiplier chips
            Text(
                "MULTIPLIER",
                style = LsfgTypography.labelMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(2, 3, 4).forEach { mult ->
                    MultiplierChip(
                        label = "×$mult",
                        selected = config.multiplier == mult,
                        onClick = { onConfigChange(config.copy(multiplier = mult)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Flow Scale
            SettingRow(
                label = "Flow Scale",
                description = "Optical flow quality (lower = faster)",
            ) {
                Text(
                    "%.2f".format(config.flowScale),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = RedCore,
                )
            }
            GamingSlider(
                value = config.flowScale,
                onValueChange = { onConfigChange(config.copy(flowScale = it)) },
                valueRange = 0.25f..1.0f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Mode toggles
            SettingRow(
                label = "Performance Mode",
                description = "LSFG 3.1P — faster, slightly lower quality",
            ) {
                GamingSwitch(
                    checked = config.performanceMode,
                    onCheckedChange = { onConfigChange(config.copy(performanceMode = it)) },
                )
            }
            SettingRow(
                label = "FP16 Shaders",
                description = "Half-precision — GPU must support shaderFloat16",
            ) {
                GamingSwitch(
                    checked = config.framegenFp16,
                    onCheckedChange = { onConfigChange(config.copy(framegenFp16 = it)) },
                )
            }
            SettingRow(
                label = "Anti-Artifacts",
                description = "Suppress interpolation on high-motion frames",
            ) {
                GamingSwitch(
                    checked = config.antiArtifacts,
                    onCheckedChange = { onConfigChange(config.copy(antiArtifacts = it)) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Post-Processing quick toggles ──────────────────────────────
        SectionHeader(
            title = "Post-Processing",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PostToggleCard(
                label = "NPU",
                icon = Icons.Default.Memory,
                enabled = config.npuPostProcessingEnabled,
                onToggle = { onConfigChange(config.copy(npuPostProcessingEnabled = it)) },
                modifier = Modifier.weight(1f),
            )
            PostToggleCard(
                label = "CPU",
                icon = Icons.Default.Tune,
                enabled = config.cpuPostProcessingEnabled,
                onToggle = { onConfigChange(config.copy(cpuPostProcessingEnabled = it)) },
                modifier = Modifier.weight(1f),
            )
            PostToggleCard(
                label = "GPU",
                icon = Icons.Default.Videocam,
                enabled = config.gpuPostProcessingEnabled,
                onToggle = { onConfigChange(config.copy(gpuPostProcessingEnabled = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Live pacing bars ──────────────────────────────────────────
        if (isSessionActive) {
            SectionHeader(
                title = "Frame Pacing",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            GamingCard(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)) {
                val targetFps = if (config.targetFpsCap > 0) config.targetFpsCap.toFloat() else 120f
                StatBar(
                    label = "Posted FPS",
                    value = (postedFps / targetFps).toFloat(),
                    displayText = "%.1f / %.0f fps".format(postedFps, targetFps),
                    accentColor = NeonCyan,
                )
                Spacer(Modifier.height(12.dp))
                StatBar(
                    label = "Latency",
                    value = (1f - (latencyMs / 50.0).toFloat()).coerceIn(0f, 1f),
                    displayText = "%.1f ms".format(latencyMs),
                    accentColor = NeonGreen,
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    isSessionActive: Boolean,
    glowAlpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF120010), SpaceBlack),
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App logo text mark
            Text(
                text = "DEEP",
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 3.sp,
                color = TextPrimary,
            )
            Text(
                text = "DROP",
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 3.sp,
                color = RedCore,
            )

            Spacer(Modifier.width(8.dp))

            // Version tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RedAlpha15)
                    .border(1.dp, RedAlpha30, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "FG",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = RedCore,
                )
            }

            Spacer(Modifier.weight(1f))

            // Active indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = isSessionActive)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSessionActive) "ACTIVE" else "STANDBY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    color = if (isSessionActive) NeonGreen.copy(alpha = glowAlpha) else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun PostToggleCard(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (enabled) RedCore.copy(0.5f) else SpaceBorder
    val bg = if (enabled) RedAlpha15 else SpaceSurface

    Column(
        modifier = modifier
            .clip(CardShapeSmall)
            .background(bg)
            .border(1.dp, borderColor, CardShapeSmall)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle(!enabled) }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) RedCore else TextMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = if (enabled) TextPrimary else TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (enabled) "ON" else "OFF",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = if (enabled) NeonGreen else TextDisabled,
        )
    }
}
