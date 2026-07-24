package com.lsfg.android.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lsfg.android.prefs.FramegenEngine
import com.lsfg.android.prefs.LsfgPreferences
import com.lsfg.android.session.TfliteFrameGenEngine
import com.lsfg.android.ui.components.IconBadge
import com.lsfg.android.ui.components.LsfgCard
import com.lsfg.android.ui.components.LsfgTopBar
import com.lsfg.android.ui.theme.LsfgPrimary
import com.lsfg.android.ui.theme.LsfgStatusGood
import com.lsfg.android.ui.theme.LsfgStatusWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Import screen for the AI_LITE frame-generation engine's .tflite model
 * (a small two-frame interpolation network — see README for how to
 * convert one, e.g. from RIFE-lite, and quantize it to shrink RAM use
 * further). Mirrors [DllPickerScreen]'s pick → validate → cache flow, but
 * there's no translation step: the file is just copied into app-private
 * storage and probe-loaded once to confirm TFLite/NNAPI can run it.
 */
@Composable
fun ModelPickerScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { LsfgPreferences(ctx) }
    val state by produceConfigState(prefs).collectAsState()

    var pickError by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probeMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = ctx.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "model.tflite"
        if (!name.endsWith(".tflite", ignoreCase = true)) {
            pickError = "Selected file is \"$name\", expected a \".tflite\" model."
            return@rememberLauncherForActivityResult
        }
        pickError = null
        probing = true
        probeMessage = null

        val dest = File(TfliteFrameGenEngine.modelDir(ctx), "framegen.tflite")
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Couldn't open the selected file")
        }.onFailure { e ->
            probing = false
            probeMessage = "Copy failed: ${e.message}"
            return@rememberLauncherForActivityResult
        }

        prefs.setTfliteModel(uri.toString(), name)
        refreshConfigState(prefs)
    }

    // Probe-load the copied model once on a background thread to confirm the
    // interpreter can actually initialize (bad graphs, unsupported ops, etc.)
    // before flipping tfliteModelReady, which is what gates the engine picker.
    androidx.compose.runtime.LaunchedEffect(state.tfliteModelDisplayName, probing) {
        if (!probing) return@LaunchedEffect
        val dest = File(TfliteFrameGenEngine.modelDir(ctx), "framegen.tflite")
        val ok = withContext(Dispatchers.IO) {
            val engine = TfliteFrameGenEngine(ctx, cpuThreads = state.tfliteCpuThreads)
            val loaded = dest.exists() && engine.load(dest)
            val usingNnapi = engine.usingNnapiAccelerator
            engine.close()
            loaded to usingNnapi
        }
        prefs.setTfliteModelReady(ok.first)
        refreshConfigState(prefs)
        probeMessage = if (ok.first) {
            if (ok.second) "Model loaded — running on a dedicated NNAPI accelerator."
            else "Model loaded — no NPU accelerator found, using CPU (slower, more RAM)."
        } else {
            "Model failed to load. Check it's a valid 2-frame interpolation .tflite graph."
        }
        probing = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LsfgTopBar(title = "AI-lite model", onBack = { nav.popBackStack() })

        val statusIcon: ImageVector
        val statusTint = when {
            probeMessage?.startsWith("Model failed") == true -> {
                statusIcon = Icons.Filled.Error
                MaterialTheme.colorScheme.error
            }
            state.tfliteModelReady -> {
                statusIcon = Icons.Filled.CheckCircle
                LsfgStatusGood
            }
            state.tfliteModelDisplayName != null -> {
                statusIcon = Icons.AutoMirrored.Filled.InsertDriveFile
                LsfgStatusWarn
            }
            else -> {
                statusIcon = Icons.AutoMirrored.Filled.InsertDriveFile
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        }

        LsfgCard(accent = state.tfliteModelReady) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = statusIcon, tint = statusTint, size = 48.dp)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.tfliteModelDisplayName ?: "No model selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = probeMessage ?: if (probing) "Loading…" else
                            if (state.tfliteModelReady) "Ready. Selectable as the frame-gen engine."
                            else "Pick a small two-frame interpolation .tflite model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (pickError != null) {
                Spacer(Modifier.size(8.dp))
                Text(pickError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        LsfgCard {
            Text("SOURCE", style = MaterialTheme.typography.labelSmall, color = LsfgPrimary)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Any two-frame interpolation network exported to TFLite works (e.g. a " +
                    "RIFE-lite build, ~a few MB, int8-quantized recommended). See the README " +
                    "\"AI-lite framegen\" section for conversion steps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !probing,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LsfgPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Choose .tflite file")
            }
        }

        LsfgCard {
            Text("ENGINE", style = MaterialTheme.typography.labelSmall, color = LsfgPrimary)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Switch which backend generates frames. AI-lite only becomes " +
                    "selectable once a model above is Ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineChoiceButton(
                    label = "LSFG shader",
                    selected = state.framegenEngine == FramegenEngine.LSFG_SHADER,
                    onClick = { prefs.setFramegenEngine(FramegenEngine.LSFG_SHADER); refreshConfigState(prefs) },
                    modifier = Modifier.weight(1f),
                )
                EngineChoiceButton(
                    label = "AI-lite",
                    selected = state.framegenEngine == FramegenEngine.AI_LITE,
                    enabled = state.tfliteModelReady,
                    onClick = { prefs.setFramegenEngine(FramegenEngine.AI_LITE); refreshConfigState(prefs) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EngineChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) LsfgPrimary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Text(label)
    }
}
