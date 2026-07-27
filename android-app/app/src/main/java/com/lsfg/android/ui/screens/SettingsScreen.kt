package com.lsfg.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.prefs.*
import com.lsfg.android.ui.components.*
import com.lsfg.android.ui.theme.*

@Composable
fun SettingsScreen(
    config: LsfgConfig,
    onConfigChange: (LsfgConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var selectedPostTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
    ) {
        // Screen title bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceNavy)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                "SETTINGS",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Frame Generation ─────────────────────────────────────────────
        SectionHeader("Frame Generation", modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))

        GamingCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

            // Multiplier
            Text("MULTIPLIER", style = LsfgTypography.labelMedium, color = TextMuted)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // Flow Scale slider
            SettingRow(
                label = "Flow Scale",
                description = "${"%.2f".format(config.flowScale)} — optical flow quality",
            ) {
                Text("%.2f".format(config.flowScale),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = RedCore)
            }
            GamingSlider(config.flowScale, { onConfigChange(config.copy(flowScale = it)) },
                valueRange = 0.25f..1.0f, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))

            SettingRow("Performance Mode",
                "LSFG_3_1P — faster, slightly reduced quality") {
                GamingSwitch(config.performanceMode) { onConfigChange(config.copy(performanceMode = it)) }
            }
            HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
            SettingRow("FP16 Shaders",
                "Requires GPU shaderFloat16 + FP16 SPIR-V cache") {
                GamingSwitch(config.framegenFp16) { onConfigChange(config.copy(framegenFp16 = it)) }
            }
            HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
            SettingRow("Anti-Artifacts",
                "Skip frames with extreme motion vectors") {
                GamingSwitch(config.antiArtifacts) { onConfigChange(config.copy(antiArtifacts = it)) }
            }
            HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
            SettingRow("HDR Mode", "Pass through HDR metadata to display") {
                GamingSwitch(config.hdr) { onConfigChange(config.copy(hdr = it)) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Post-Processing ───────────────────────────────────────────────
        SectionHeader("Post-Processing", modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))

        TabRow(
            selectedTabIndex = selectedPostTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = SpaceSurface,
            contentColor = RedCore,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedPostTab]),
                    color = RedCore,
                )
            },
        ) {
            listOf("NPU", "CPU", "GPU").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedPostTab == idx,
                    onClick = { selectedPostTab = idx },
                    text = {
                        Text(label, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 1.sp,
                            color = if (selectedPostTab == idx) RedCore else TextMuted)
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedPostTab) {
            0 -> NpuSettingsPanel(config, onConfigChange)
            1 -> CpuSettingsPanel(config, onConfigChange)
            2 -> GpuSettingsPanel(config, onConfigChange)
        }

        Spacer(Modifier.height(20.dp))

        // ── Pacing & Timing ───────────────────────────────────────────────
        SectionHeader("Pacing & Timing", modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))

        GamingCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SettingRow("Vsync Refresh", "Override display refresh rate") {
                Text(config.vsyncRefreshOverride.label,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonCyan)
            }
            HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)

            SettingRow("Target FPS Cap",
                if (config.targetFpsCap == 0) "Unlimited" else "${config.targetFpsCap} fps") {
                Text(if (config.targetFpsCap == 0) "∞" else "${config.targetFpsCap}",
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = RedCore)
            }
            GamingSlider(config.targetFpsCap.toFloat() / 240f,
                { onConfigChange(config.copy(targetFpsCap = (it * 240).toInt())) },
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            SettingRow("Queue Depth",
                "Frame buffer depth: ${config.queueDepth} frames") {
                Text("${config.queueDepth}",
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = NeonOrange)
            }
            GamingSlider((config.queueDepth - 2).toFloat() / 4f,
                { onConfigChange(config.copy(queueDepth = (it * 4).toInt() + 2)) },
                steps = 3, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            SettingRow("Vsync Slack",
                "Pacing tolerance: %.1f ms".format(config.vsyncSlackMs)) {
                Text("%.1f ms".format(config.vsyncSlackMs),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = NeonGreen)
            }
            GamingSlider((config.vsyncSlackMs - 1f) / 4f,
                { onConfigChange(config.copy(vsyncSlackMs = 1f + it * 4f)) },
                modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(20.dp))

        // ── HUD & Misc ────────────────────────────────────────────────────
        SectionHeader("HUD & Misc", modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))

        GamingCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SettingRow("Show HUD Overlay", "Display live stats over your game") {
                GamingSwitch(config.showHud) { onConfigChange(config.copy(showHud = it)) }
            }
            HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
            SettingRow("Shizuku Timing", "Use Shizuku metrics side-channel for pacing") {
                GamingSwitch(config.shizukuTimingEnabled) {
                    onConfigChange(config.copy(shizukuTimingEnabled = it))
                }
            }
            HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
            SettingRow("EMA Alpha",
                "Pacing smoothing: %.3f".format(config.emaAlpha)) {
                Text("%.3f".format(config.emaAlpha),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = TextSecondary)
            }
            GamingSlider((config.emaAlpha - 0.05f) / 0.45f,
                { onConfigChange(config.copy(emaAlpha = 0.05f + it * 0.45f)) },
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NpuSettingsPanel(config: LsfgConfig, onConfigChange: (LsfgConfig) -> Unit) {
    GamingCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        glowColor = NeonCyan) {
        SettingRow("Enable NPU Post-Processing", null) {
            GamingSwitch(config.npuPostProcessingEnabled) {
                onConfigChange(config.copy(npuPostProcessingEnabled = it))
            }
        }
        HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
        SettingRow("Preset", config.npuPostProcessingPreset.label) {
            Text(config.npuPostProcessingPreset.label,
                fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
        }
        // Preset selector chips
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NpuPostProcessingPreset.entries.filter { it != NpuPostProcessingPreset.OFF }
                .forEach { preset ->
                    val sel = config.npuPostProcessingPreset == preset
                    MultiplierChip(
                        label = preset.label.take(5),
                        selected = sel,
                        onClick = { onConfigChange(config.copy(npuPostProcessingPreset = preset)) },
                        modifier = Modifier.weight(1f),
                    )
                }
        }
        Spacer(Modifier.height(12.dp))
        SettingRow("Amount", "%.2f".format(config.npuAmount)) {
            Text("%.2f".format(config.npuAmount),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = NeonCyan)
        }
        GamingSlider(config.npuAmount, { onConfigChange(config.copy(npuAmount = it)) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SettingRow("Upscale Factor",
            if (config.npuUpscaleFactor == 2) "2× (NPU resize)" else "1× (enhance only)") {
            GamingSwitch(config.npuUpscaleFactor == 2) {
                onConfigChange(config.copy(npuUpscaleFactor = if (it) 2 else 1))
            }
        }
        HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
        SettingRow("FP16 (NPU)", "Use relaxed FP16 inference on NPU") {
            GamingSwitch(config.npuFp16) { onConfigChange(config.copy(npuFp16 = it)) }
        }
    }
}

@Composable
private fun CpuSettingsPanel(config: LsfgConfig, onConfigChange: (LsfgConfig) -> Unit) {
    GamingCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        glowColor = NeonOrange) {
        SettingRow("Enable CPU Post-Processing", null) {
            GamingSwitch(config.cpuPostProcessingEnabled) {
                onConfigChange(config.copy(cpuPostProcessingEnabled = it))
            }
        }
        HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
        SettingRow("Strength", "%.2f".format(config.cpuStrength)) {
            Text("%.2f".format(config.cpuStrength),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = NeonOrange)
        }
        GamingSlider(config.cpuStrength, { onConfigChange(config.copy(cpuStrength = it)) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SettingRow("Saturation", "%.2f (0.5 = neutral)".format(config.cpuSaturation)) {
            Text("%.2f".format(config.cpuSaturation),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = NeonOrange)
        }
        GamingSlider(config.cpuSaturation, { onConfigChange(config.copy(cpuSaturation = it)) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SettingRow("Vignette", "%.2f".format(config.cpuVignette)) {
            Text("%.2f".format(config.cpuVignette),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = NeonOrange)
        }
        GamingSlider(config.cpuVignette, { onConfigChange(config.copy(cpuVignette = it)) },
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GpuSettingsPanel(config: LsfgConfig, onConfigChange: (LsfgConfig) -> Unit) {
    GamingCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        glowColor = NeonGreen) {
        SettingRow("Enable GPU Post-Processing", null) {
            GamingSwitch(config.gpuPostProcessingEnabled) {
                onConfigChange(config.copy(gpuPostProcessingEnabled = it))
            }
        }
        HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
        SettingRow("Stage", if (config.gpuStage == 0) "Before LSFG" else "After LSFG") {
            GamingSwitch(config.gpuStage == 1) {
                onConfigChange(config.copy(gpuStage = if (it) 1 else 0))
            }
        }
        HorizontalDivider(color = SpaceBorder, thickness = 0.5.dp)
        SettingRow("Sharpness", "%.2f".format(config.gpuSharpness)) {
            Text("%.2f".format(config.gpuSharpness),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = NeonGreen)
        }
        GamingSlider(config.gpuSharpness, { onConfigChange(config.copy(gpuSharpness = it)) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SettingRow("Strength", "%.2f".format(config.gpuStrength)) {
            Text("%.2f".format(config.gpuStrength),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = NeonGreen)
        }
        GamingSlider(config.gpuStrength, { onConfigChange(config.copy(gpuStrength = it)) },
            modifier = Modifier.fillMaxWidth())
    }
}
