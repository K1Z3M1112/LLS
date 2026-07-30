package com.lsfg.android.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsfg.android.LsfgApplication
import com.lsfg.android.prefs.*
import com.lsfg.android.ui.components.*
import com.lsfg.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ─────────────────────────────────────────────────────────────────────────────
// Setup screen — stateful, self-contained.
// Handles:
//   • Lossless.dll import + shader extraction
//   • NCNN AI model setup (bundled FP32/FP16 + custom upload)
// ─────────────────────────────────────────────────────────────────────────────

enum class SetupPhase { PICK_DLL, EXTRACTING, PROBING, DONE, ERROR }

private sealed class ExtractionUiState {
    data object Idle : ExtractionUiState()
    data object Running : ExtractionUiState()
    data class Done(val success: Boolean, val message: String) : ExtractionUiState()
}

private sealed class CopyState {
    data object Idle : CopyState()
    data object Running : CopyState()
    data class Done(val success: Boolean, val message: String) : CopyState()
}

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { LsfgPreferences(ctx) }
    var config by remember { mutableStateOf(prefs.load()) }

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

        // Header
        Text("DEEPDROP", fontWeight = FontWeight.Black,
            fontSize = 28.sp, letterSpacing = 4.sp, color = TextPrimary)
        Text("Frame Generation for Android",
            fontSize = 12.sp, letterSpacing = 1.sp, color = TextSecondary)

        Spacer(Modifier.height(28.dp))

        // ── Engine Mode Selector ─────────────────────────────────────────
        EngineModeSelector(
            selected = config.aiEngineMode,
            onSelect = {
                prefs.setAiEngineMode(it)
                config = prefs.load()
            },
        )

        Spacer(Modifier.height(20.dp))

        // ── Per-mode panel ───────────────────────────────────────────────
        when (config.aiEngineMode) {
            AiEngineMode.LOSSLESS_DLL -> DllSetupPanel(
                config = config,
                prefs = prefs,
                onConfigChanged = { config = prefs.load() },
            )
            AiEngineMode.NCNN_AI -> NcnnSetupPanel(
                config = config,
                prefs = prefs,
                onConfigChanged = { config = prefs.load() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Engine Mode Selector — the top toggle between DLL and AI model
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EngineModeSelector(
    selected: AiEngineMode,
    onSelect: (AiEngineMode) -> Unit,
) {
    GamingCard(modifier = Modifier.fillMaxWidth()) {
        Text("FRAMEGEN ENGINE", style = LsfgTypography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EngineModeChip(
                label = "Lossless.dll",
                subLabel = "Vulkan Pipeline",
                icon = Icons.Default.Extension,
                selected = selected == AiEngineMode.LOSSLESS_DLL,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(AiEngineMode.LOSSLESS_DLL) },
            )
            EngineModeChip(
                label = "AI Model",
                subLabel = "NCNN Backend",
                icon = Icons.Default.Memory,
                selected = selected == AiEngineMode.NCNN_AI,
                accentColor = NeonCyan,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(AiEngineMode.NCNN_AI) },
            )
        }
    }
}

@Composable
private fun EngineModeChip(
    label: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RedCore,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) accentColor else SpaceBorder
    val bgColor = if (selected) accentColor.copy(alpha = 0.10f) else SpaceSurface2

    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(CardShapeSmall)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = CardShapeSmall,
            ),
        color = bgColor,
        shape = CardShapeSmall,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                icon, null,
                tint = if (selected) accentColor else TextMuted,
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (selected) accentColor else TextSecondary,
            )
            Text(
                subLabel,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp,
                color = TextMuted,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lossless.dll panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DllSetupPanel(
    config: LsfgConfig,
    prefs: LsfgPreferences,
    onConfigChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    var extractionState by remember { mutableStateOf<ExtractionUiState>(ExtractionUiState.Idle) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = ctx.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = resolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Lossless.dll"

        if (!name.equals("Lossless.dll", ignoreCase = true)) {
            pickError = "Expected \"Lossless.dll\", got \"$name\"."
            return@rememberLauncherForActivityResult
        }
        pickError = null
        prefs.setDllPath(uri.toString(), "")
        onConfigChanged()
        pendingUri = uri
        extractionState = ExtractionUiState.Running
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        if (extractionState !is ExtractionUiState.Running) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val input = ctx.contentResolver.openInputStream(uri)
                    ?: return@runCatching "Cannot open DLL stream"
                val cacheDir = File(ctx.filesDir, "spirv")
                cacheDir.mkdirs()
                val dest = File(cacheDir, "Lossless.dll")
                FileOutputStream(dest).use { input.copyTo(it) }
                input.close()
                prefs.setDllPath(dest.absolutePath, "")
                null // no error
            }.getOrElse { it.message ?: "Unknown error" }
        }
        pendingUri = null
        extractionState = if (result == null)
            ExtractionUiState.Done(true, "DLL copied — ready to probe shaders")
        else
            ExtractionUiState.Done(false, result)
        onConfigChanged()
    }

    // Step indicator
    val phase = when (extractionState) {
        is ExtractionUiState.Idle ->
            if (config.dllPath.isNotEmpty()) SetupPhase.DONE else SetupPhase.PICK_DLL
        is ExtractionUiState.Running -> SetupPhase.EXTRACTING
        is ExtractionUiState.Done ->
            if ((extractionState as ExtractionUiState.Done).success) SetupPhase.DONE
            else SetupPhase.ERROR
    }

    StepIndicator(currentPhase = phase)
    Spacer(Modifier.height(20.dp))

    GamingCard(modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FolderOpen, null, tint = RedCore,
            modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        Text("Import Lossless.dll",
            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text(
            "Requires a legitimately-owned Steam copy of Lossless Scaling. " +
                "The DLL is never bundled here.",
            fontSize = 11.sp, color = TextSecondary, lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        // Current file status
        val hasFile = config.dllPath.isNotEmpty()
        if (hasFile) {
            StatusRow(
                label = config.dllPath.substringAfterLast('/'),
                isOk = phase == SetupPhase.DONE,
                detail = when (extractionState) {
                    is ExtractionUiState.Done ->
                        (extractionState as ExtractionUiState.Done).message
                    else -> "DLL selected"
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (extractionState is ExtractionUiState.Running) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = RedCore, trackColor = SpaceSurface2,
            )
            Spacer(Modifier.height(8.dp))
            Text("Copying DLL…", fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
        }

        pickError?.let {
            Text(it, fontSize = 11.sp, color = ErrorRed)
            Spacer(Modifier.height(8.dp))
        }

        OutlineButton(
            label = if (!hasFile) "Pick Lossless.dll" else "Change DLL",
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = extractionState !is ExtractionUiState.Running,
            modifier = Modifier.fillMaxWidth(),
            accentColor = NeonCyan,
            icon = { Icon(Icons.Default.FolderOpen, null, tint = NeonCyan,
                modifier = Modifier.size(16.dp)) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NCNN AI Model panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NcnnSetupPanel(
    config: LsfgConfig,
    prefs: LsfgPreferences,
    onConfigChanged: () -> Unit,
) {
    val ctx = LocalContext.current

    // Check if bundled models exist on disk (copied from assets in LsfgApplication)
    val bundledFp32Ready = remember {
        File(LsfgApplication.bundledParamPath(ctx.filesDir, "fp32")).exists() &&
            File(LsfgApplication.bundledParamPath(ctx.filesDir, "fp32")
                .replace(".param", ".bin")).exists()
    }
    val bundledFp16Ready = remember {
        File(LsfgApplication.bundledParamPath(ctx.filesDir, "fp16")).exists() &&
            File(LsfgApplication.bundledParamPath(ctx.filesDir, "fp16")
                .replace(".param", ".bin")).exists()
    }

    // Custom model copy state
    var customParamCopyState by remember { mutableStateOf<CopyState>(CopyState.Idle) }
    var customBinCopyState by remember { mutableStateOf<CopyState>(CopyState.Idle) }
    var pendingParamUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBinUri by remember { mutableStateOf<Uri?>(null) }
    var pendingParamName by remember { mutableStateOf("") }
    var pendingBinName by remember { mutableStateOf("") }

    // SAF launchers for custom model
    val paramPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "model.param"
        pendingParamName = name
        pendingParamUri = uri
        customParamCopyState = CopyState.Running
    }
    val binPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "model.bin"
        pendingBinName = name
        pendingBinUri = uri
        customBinCopyState = CopyState.Running
    }

    // Copy .param to internal storage
    LaunchedEffect(pendingParamUri) {
        val uri = pendingParamUri ?: return@LaunchedEffect
        val destDir = File(ctx.filesDir, "ncnn_custom").also { it.mkdirs() }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dest = File(destDir, "custom_model.ncnn.param")
                ctx.contentResolver.openInputStream(uri)!!.use { inp ->
                    FileOutputStream(dest).use { inp.copyTo(it) }
                }
                prefs.setNcnnCustomModel(dest.absolutePath, pendingParamName)
                null
            }.getOrElse { it.message ?: "Copy failed" }
        }
        pendingParamUri = null
        customParamCopyState = if (result == null)
            CopyState.Done(true, "Copied") else CopyState.Done(false, result)
        onConfigChanged()
    }

    // Copy .bin to internal storage
    LaunchedEffect(pendingBinUri) {
        val uri = pendingBinUri ?: return@LaunchedEffect
        val destDir = File(ctx.filesDir, "ncnn_custom").also { it.mkdirs() }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dest = File(destDir, "custom_model.ncnn.bin")
                ctx.contentResolver.openInputStream(uri)!!.use { inp ->
                    FileOutputStream(dest).use { inp.copyTo(it) }
                }
                null
            }.getOrElse { it.message ?: "Copy failed" }
        }
        pendingBinUri = null
        customBinCopyState = if (result == null)
            CopyState.Done(true, "Copied") else CopyState.Done(false, result)
        onConfigChanged()
    }

    // ── Precision chip ───────────────────────────────────────────────────
    GamingCard(modifier = Modifier.fillMaxWidth()) {
        Text("AI ENGINE", style = LsfgTypography.labelMedium, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "NCNN backend uses a custom IFNetLite/RIFE-lite model trained for mobile. " +
                "No DLL required — works on any Vulkan-capable Android GPU.",
            fontSize = 11.sp, color = TextSecondary, lineHeight = 17.sp,
        )

        Spacer(Modifier.height(14.dp))
        Text("PRECISION", style = LsfgTypography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NcnnPrecision.entries.forEach { prec ->
                val available = if (prec == NcnnPrecision.FP32) bundledFp32Ready
                                else bundledFp16Ready
                PrecisionChip(
                    label = prec.label,
                    subLabel = when (prec) {
                        NcnnPrecision.FP32 -> "Higher quality"
                        NcnnPrecision.FP16 -> "Faster · saves VRAM"
                    },
                    selected = config.ncnnPrecision == prec,
                    available = available,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        prefs.setNcnnPrecision(prec)
                        onConfigChanged()
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bundled model status
        val activePrecReady = if (config.ncnnPrecision == NcnnPrecision.FP32)
            bundledFp32Ready else bundledFp16Ready
        StatusRow(
            label = "Bundled Model (${config.ncnnPrecision.label})",
            isOk = activePrecReady,
            detail = if (activePrecReady) "Ready — frame_interpolator_v2_${config.ncnnPrecision.value}.ncnn"
                     else "Not found — check assets were compiled in",
        )
    }

    Spacer(Modifier.height(14.dp))

    // ── Model source ─────────────────────────────────────────────────────
    GamingCard(modifier = Modifier.fillMaxWidth()) {
        Text("MODEL SOURCE", style = LsfgTypography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelSourceChip(
                label = "Bundled",
                selected = !config.ncnnUseCustomModel,
                modifier = Modifier.weight(1f),
                onClick = {
                    prefs.setNcnnUseCustomModel(false)
                    onConfigChanged()
                },
            )
            ModelSourceChip(
                label = "Custom Upload",
                selected = config.ncnnUseCustomModel,
                accentColor = NeonOrange,
                modifier = Modifier.weight(1f),
                onClick = {
                    prefs.setNcnnUseCustomModel(true)
                    onConfigChanged()
                },
            )
        }

        if (config.ncnnUseCustomModel) {
            Spacer(Modifier.height(14.dp))
            Text("CUSTOM MODEL FILES", style = LsfgTypography.labelMedium, color = NeonOrange)
            Spacer(Modifier.height(4.dp))
            Text(
                "Upload a .param + .bin pair exported from the MobileFrameGen notebook. " +
                    "Both files must be the same model version.",
                fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))

            // .param upload row
            val paramDone = customParamCopyState is CopyState.Done &&
                (customParamCopyState as CopyState.Done).success
            val paramPath = config.ncnnCustomParamPath
            val paramHasFile = paramDone || paramPath.isNotEmpty()

            ModelFileRow(
                label = ".param file",
                fileName = when {
                    paramDone -> pendingParamName
                    paramPath.isNotEmpty() -> config.ncnnCustomModelName.ifEmpty {
                        paramPath.substringAfterLast('/')
                    }
                    else -> null
                },
                copyState = customParamCopyState,
                onPick = { paramPicker.launch(arrayOf("*/*")) },
                enabled = customParamCopyState !is CopyState.Running &&
                    customBinCopyState !is CopyState.Running,
            )

            Spacer(Modifier.height(8.dp))

            // .bin upload row
            val binDone = customBinCopyState is CopyState.Done &&
                (customBinCopyState as CopyState.Done).success
            val binPath = if (paramPath.isNotEmpty())
                paramPath.replace(".param", ".bin") else ""
            val binHasFile = binDone || File(binPath).exists()

            ModelFileRow(
                label = ".bin file",
                fileName = when {
                    binDone -> pendingBinName
                    binHasFile -> binPath.substringAfterLast('/')
                    else -> null
                },
                copyState = customBinCopyState,
                onPick = { binPicker.launch(arrayOf("*/*")) },
                enabled = customParamCopyState !is CopyState.Running &&
                    customBinCopyState !is CopyState.Running,
            )

            if (paramHasFile && binHasFile) {
                Spacer(Modifier.height(10.dp))
                StatusRow(
                    label = "Custom model",
                    isOk = true,
                    detail = "Both files present — ready for inference",
                )
            }

            if (paramHasFile || binHasFile) {
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    label = "Clear Custom Model",
                    onClick = {
                        prefs.clearNcnnCustomModel()
                        customParamCopyState = CopyState.Idle
                        customBinCopyState = CopyState.Idle
                        onConfigChanged()
                    },
                    accentColor = ErrorRed,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(Icons.Default.DeleteOutline, null, tint = ErrorRed,
                            modifier = Modifier.size(15.dp))
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // ── Active model summary ─────────────────────────────────────────────
    val activeDesc = buildString {
        if (config.ncnnUseCustomModel && config.ncnnCustomParamPath.isNotEmpty())
            append("Custom: ${config.ncnnCustomModelName.ifEmpty {
                config.ncnnCustomParamPath.substringAfterLast('/')
            }}")
        else
            append("Bundled: frame_interpolator_v2_${config.ncnnPrecision.value}.ncnn")
    }

    GamingCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonCyan,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = NeonCyan,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text("ACTIVE AI ENGINE",
                    style = LsfgTypography.labelMedium, color = NeonCyan)
                Text(activeDesc, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepIndicator(currentPhase: SetupPhase) {
    val steps = listOf("Import", "Process", "Probe", "Ready")
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
                    modifier = Modifier.size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(dotColor.copy(alpha = if (active || done) 1f else 0.3f))
                        .border(1.dp, dotColor, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done)
                        Icon(Icons.Default.Check, null, tint = SpaceBlack,
                            modifier = Modifier.size(13.dp))
                    else
                        Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 10.sp,
                            color = if (active) TextPrimary else TextDisabled)
                }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 8.sp, letterSpacing = 0.3.sp,
                    color = if (active || done) TextSecondary else TextDisabled)
            }
            if (idx < steps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.weight(0.5f).padding(bottom = 14.dp),
                    color = if (idx < currentIdx) NeonGreen.copy(0.5f) else SpaceBorder,
                    thickness = 1.dp,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, isOk: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(CardShapeSmall)
            .background(SpaceSurface2)
            .border(1.dp, if (isOk) NeonGreen.copy(0.4f) else SpaceBorder, CardShapeSmall)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isOk) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (isOk) NeonGreen else TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (detail.isNotEmpty())
                Text(detail, fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PrecisionChip(
    label: String,
    subLabel: String,
    selected: Boolean,
    available: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (label == "FP32") RedCore else NeonCyan
    val borderColor = when {
        selected -> accent
        else -> SpaceBorder
    }
    val bgColor = if (selected) accent.copy(alpha = 0.12f) else SpaceSurface2

    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(CardShapeSmall)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, CardShapeSmall),
        color = bgColor,
        shape = CardShapeSmall,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 14.sp,
                color = if (selected) accent else TextSecondary)
            Text(subLabel, fontSize = 9.sp, color = TextMuted,
                textAlign = TextAlign.Center, lineHeight = 12.sp)
            if (available) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = NeonGreen, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.size(3.dp))
                    Text("Ready", fontSize = 8.sp, color = NeonGreen)
                }
            }
        }
    }
}

@Composable
private fun ModelSourceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) accentColor else SpaceBorder
    val bgColor = if (selected) accentColor.copy(alpha = 0.10f) else SpaceSurface2

    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(CardShapeSmall)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, CardShapeSmall),
        color = bgColor,
        shape = CardShapeSmall,
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (selected) accentColor else TextSecondary,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ModelFileRow(
    label: String,
    fileName: String?,
    copyState: CopyState,
    onPick: () -> Unit,
    enabled: Boolean,
) {
    val isOk = copyState is CopyState.Done && (copyState as CopyState.Done).success
    val hasFile = fileName != null

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(CardShapeSmall)
            .background(SpaceSurface2)
            .border(
                1.dp,
                when {
                    isOk -> NeonGreen.copy(0.4f)
                    copyState is CopyState.Done && !(copyState as CopyState.Done).success ->
                        ErrorRed.copy(0.4f)
                    else -> SpaceBorder
                },
                CardShapeSmall,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                isOk -> Icons.Default.CheckCircle
                copyState is CopyState.Running -> Icons.Default.Sync
                else -> Icons.Default.UploadFile
            },
            null,
            tint = when {
                isOk -> NeonGreen
                copyState is CopyState.Running -> RedCore
                else -> TextMuted
            },
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            if (copyState is CopyState.Running) {
                Text("Copying…", fontSize = 9.sp, color = TextMuted)
            } else if (hasFile) {
                Text(fileName!!, fontSize = 9.sp, color = NeonGreen,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
            } else {
                Text("Not uploaded", fontSize = 9.sp, color = TextDisabled)
            }
        }
        Spacer(Modifier.size(8.dp))
        OutlineButton(
            label = if (hasFile) "Change" else "Pick",
            onClick = onPick,
            enabled = enabled,
            accentColor = NeonOrange,
            modifier = Modifier.height(30.dp),
        )
    }
}
