package com.lsfg.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.ui.components.*
import com.lsfg.android.ui.theme.*

enum class SetupPhase { PICK_DLL, EXTRACTING, PROBING, DONE, ERROR }

@Composable
fun SetupScreen(
    phase: SetupPhase,
    statusMessage: String,
    progressFraction: Float,      // 0..1, used during EXTRACTING
    dllPath: String,
    onPickDll: () -> Unit,
    onStartExtraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // Large logo / title
        Text("DEEPDROP", fontWeight = FontWeight.Black,
            fontSize = 28.sp, letterSpacing = 4.sp, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Frame Generation for Android",
                fontSize = 12.sp, letterSpacing = 1.sp, color = TextSecondary)
        }

        Spacer(Modifier.height(32.dp))

        // Step indicator
        StepIndicator(currentPhase = phase)

        Spacer(Modifier.height(32.dp))

        when (phase) {
            SetupPhase.PICK_DLL -> PickDllPanel(dllPath = dllPath,
                onPick = onPickDll, onExtract = onStartExtraction)
            SetupPhase.EXTRACTING -> ExtractingPanel(progress = progressFraction,
                message = statusMessage)
            SetupPhase.PROBING -> ProbingPanel()
            SetupPhase.DONE -> DonePanel()
            SetupPhase.ERROR -> ErrorPanel(message = statusMessage, onRetry = onPickDll)
        }
    }
}

@Composable
private fun StepIndicator(currentPhase: SetupPhase) {
    val steps = listOf("DLL", "Extract", "Probe", "Ready")
    val currentIdx = when (currentPhase) {
        SetupPhase.PICK_DLL -> 0
        SetupPhase.EXTRACTING -> 1
        SetupPhase.PROBING -> 2
        SetupPhase.DONE -> 3
        SetupPhase.ERROR -> 1
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { idx, label ->
            val done = idx < currentIdx
            val active = idx == currentIdx
            val dotColor = when {
                done -> NeonGreen
                active -> RedCore
                else -> SpaceSurface2
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(dotColor.copy(alpha = if (active || done) 1f else 0.3f))
                        .border(1.dp, dotColor, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done)
                        Icon(Icons.Default.Check, null, tint = SpaceBlack, modifier = Modifier.size(14.dp))
                    else
                        Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            color = if (active) TextPrimary else TextDisabled)
                }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 9.sp, letterSpacing = 0.5.sp,
                    color = if (active || done) TextSecondary else TextDisabled)
            }
            if (idx < steps.size - 1) {
                HorizontalDivider(modifier = Modifier.weight(0.5f).padding(bottom = 16.dp),
                    color = if (idx < currentIdx) NeonGreen.copy(0.5f) else SpaceBorder,
                    thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun PickDllPanel(dllPath: String, onPick: () -> Unit, onExtract: () -> Unit) {
    GamingCard(modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FolderOpen, null, tint = RedCore,
            modifier = Modifier.size(36.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text("Import Lossless.dll",
            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text(
            "DeepDrop requires the Lossless.dll from a legitimately-owned Steam copy " +
                "of Lossless Scaling. It is never bundled or distributed here.",
            fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        // Current DLL path display
        if (dllPath.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(CardShapeSmall)
                    .background(SpaceSurface2).border(1.dp, SpaceBorder, CardShapeSmall)
                    .padding(10.dp),
            ) {
                Text(
                    dllPath.substringAfterLast('/'),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonGreen,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlineButton(
            label = if (dllPath.isEmpty()) "Pick Lossless.dll" else "Change DLL",
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
            accentColor = NeonCyan,
            icon = { Icon(Icons.Default.FolderOpen, null, tint = NeonCyan, modifier = Modifier.size(16.dp)) },
        )

        if (dllPath.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            NeonButton(
                label = "Extract Shaders",
                onClick = onExtract,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Default.Build, null, tint = TextPrimary, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun ExtractingPanel(progress: Float, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = RedCore, trackColor = SpaceSurface2,
        )
        Spacer(Modifier.height(16.dp))
        Text("Extracting shaders…", fontWeight = FontWeight.Bold,
            fontSize = 16.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("${(progress * 100).toInt()}%",
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, color = RedCore)
    }
}

@Composable
private fun ProbingPanel() {
    val infiniteTransition = rememberInfiniteTransition(label = "probe")
    val alpha by infiniteTransition.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = RedCore, trackColor = SpaceSurface2, strokeWidth = 3.dp,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Probing shaders on GPU…", fontWeight = FontWeight.Bold,
            fontSize = 16.sp, color = TextPrimary.copy(alpha))
        Spacer(Modifier.height(6.dp))
        Text("vkCreateShaderModule validation running",
            fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun DonePanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Setup Complete!", fontWeight = FontWeight.Bold,
            fontSize = 20.sp, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("All shaders extracted and validated.\nReady to generate frames.",
            fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Error, null, tint = ErrorRed, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Setup Failed", fontWeight = FontWeight.Bold,
            fontSize = 20.sp, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        GamingCard(modifier = Modifier.fillMaxWidth()) {
            Text(message, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(20.dp))
        NeonButton(label = "Try Again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}
