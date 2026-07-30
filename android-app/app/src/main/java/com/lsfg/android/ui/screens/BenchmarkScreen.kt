package com.lsfg.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.benchmark.*
import com.lsfg.android.ui.components.*
import com.lsfg.android.ui.theme.*

@Composable
fun BenchmarkScreen(
    benchState: BenchmarkController.State,
    onStartBenchmark: () -> Unit,
    onCancelBenchmark: () -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
    ) {
        // Title bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceNavy)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("BENCHMARK", fontWeight = FontWeight.Black,
                fontSize = 14.sp, letterSpacing = 3.sp, color = TextPrimary)
        }

        Spacer(Modifier.height(24.dp))

        when (val s = benchState) {
            is BenchmarkController.State.Idle -> IdlePanel(onStart = onStartBenchmark)

            is BenchmarkController.State.Running ->
                RunningPanel(state = s, onCancel = onCancelBenchmark)

            is BenchmarkController.State.Completed ->
                CompletedPanel(state = s, onAcknowledge = onAcknowledge)

            is BenchmarkController.State.Failed ->
                FailedPanel(message = s.message, onAcknowledge = onAcknowledge)
        }
    }
}

// ── Idle ─────────────────────────────────────────────────────────────────────

@Composable
private fun IdlePanel(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(RedAlpha15)
                .border(1.dp, RedAlpha30, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = RedCore,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Performance Benchmark",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Runs ×2, ×3, ×4 multiplier passes (FP32 + FP16 on supported devices). " +
                "Requires an active Frame Gen session with a game running.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )

        Spacer(Modifier.height(24.dp))

        // Info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BenchInfoChip("≈ 6 min", "Total time", modifier = Modifier.weight(1f))
            BenchInfoChip("60 s", "Per run", modifier = Modifier.weight(1f))
            BenchInfoChip("5 s", "Warmup", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))

        NeonButton(
            label = "Start Benchmark",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            icon = {
                Icon(Icons.Default.PlayArrow, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            },
        )
    }
}

@Composable
private fun BenchInfoChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CardShapeSmall)
            .background(SpaceSurface)
            .border(1.dp, SpaceBorder, CardShapeSmall)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = RedCore)
        Text(label, fontSize = 10.sp, color = TextMuted, letterSpacing = 0.5.sp)
    }
}

// ── Running ───────────────────────────────────────────────────────────────────

@Composable
private fun RunningPanel(
    state: BenchmarkController.State.Running,
    onCancel: () -> Unit,
) {
    val rotation by rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), "spin")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Spinning ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(RedAlpha15)
                .border(1.dp, RedAlpha30, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(100.dp).rotate(rotation),
                color = RedCore,
                strokeWidth = 3.dp,
                trackColor = SpaceSurface2,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.runIndex + 1} / ${state.totalRuns}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = TextPrimary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Run info
        Text(
            "${state.phase.name} — ×${state.multiplier} ${state.precision.label}",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (state.phase) {
                BenchmarkController.Phase.WARMUP -> "Allowing pipeline to stabilise…"
                BenchmarkController.Phase.SAMPLING -> "Collecting frame timing data…"
            },
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Overall progress bar
        val totalProgress = state.runIndex.toFloat() / state.totalRuns.toFloat()
        StatBar(
            label = "Overall Progress",
            value = totalProgress,
            displayText = "${((totalProgress * 100).toInt())}%",
            accentColor = RedCore,
        )

        Spacer(Modifier.height(28.dp))

        OutlineButton(
            label = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            accentColor = RedCore,
            icon = {
                Icon(Icons.Default.Close, null, tint = RedCore, modifier = Modifier.size(16.dp))
            },
        )
    }
}

// ── Completed ────────────────────────────────────────────────────────────────

@Composable
private fun CompletedPanel(
    state: BenchmarkController.State.Completed,
    onAcknowledge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NeonGreen.copy(0.12f))
                .border(1.dp, NeonGreen.copy(0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text("Benchmark Complete", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        Spacer(Modifier.height(20.dp))

        // Results cards
        state.results.forEach { result ->
            ResultCard(result = result)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(20.dp))

        NeonButton(label = "Done", onClick = onAcknowledge, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultCard(result: BenchmarkRunResult) {
    GamingCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (result.framegenFp16) NeonCyan else RedCore,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "×${result.multiplier} ${if (result.framegenFp16) "FP16" else "FP32"}",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
                )
                Text("%.1f s".format(result.runDurationMs / 1000.0),
                    fontSize = 11.sp, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.1f fps".format(result.postedFps),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = if (result.framegenFp16) NeonCyan else RedCore)
                Text("posted", fontSize = 10.sp, color = TextMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(modifier = Modifier.weight(1f), accentColor = NeonGreen,
                innerPadding = PaddingValues(8.dp)) {
                Text("REAL", style = LsfgTypography.labelSmall)
                Text("%.1f".format(result.realFps),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = NeonGreen)
                Text("fps", style = LsfgTypography.labelSmall)
            }
            StatCard(modifier = Modifier.weight(1f), accentColor = NeonOrange,
                innerPadding = PaddingValues(8.dp)) {
                val p50 = result.pacingMs?.p50Ms ?: 0.0
                Text("P50", style = LsfgTypography.labelSmall)
                Text("%.2f".format(p50),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = NeonOrange)
                Text("ms", style = LsfgTypography.labelSmall)
            }
            StatCard(modifier = Modifier.weight(1f), accentColor = TextSecondary,
                innerPadding = PaddingValues(8.dp)) {
                Text("STALLS", style = LsfgTypography.labelSmall)
                Text("${result.stallCount}",
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (result.stallCount == 0) NeonGreen else NeonOrange)
                Text("drops", style = LsfgTypography.labelSmall)
            }
        }
    }
}

// ── Failed ────────────────────────────────────────────────────────────────────

@Composable
private fun FailedPanel(message: String, onAcknowledge: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .background(RedCore.copy(0.12f)).border(1.dp, RedCore.copy(0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = RedCore, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Benchmark Failed", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        GamingCard(modifier = Modifier.fillMaxWidth()) {
            Text(message, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
        NeonButton(label = "Dismiss", onClick = onAcknowledge, modifier = Modifier.fillMaxWidth())
    }
}
