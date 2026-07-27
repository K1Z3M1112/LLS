package com.lsfg.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lsfg.android.benchmark.BenchmarkController
import com.lsfg.android.prefs.LsfgConfig
import com.lsfg.android.session.NativeBridge
import com.lsfg.android.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // Shared lightweight state (production code would use a ViewModel/Service binding)
    var config by remember { mutableStateOf(LsfgConfig()) }
    var isSessionActive by remember { mutableStateOf(false) }
    var generatedFps by remember { mutableDoubleStateOf(0.0) }
    var realFps by remember { mutableDoubleStateOf(0.0) }
    var postedFps by remember { mutableDoubleStateOf(0.0) }
    var latencyMs by remember { mutableDoubleStateOf(0.0) }
    var setupPhase by remember { mutableStateOf(SetupPhase.PICK_DLL) }
    var setupMessage by remember { mutableStateOf("") }
    var dllPath by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Copies the picked content:// URI into app-private storage (NativeBridge
    // needs a real filesystem path, not a SAF URI) and leaves `setupPhase`
    // at PICK_DLL so the user can still hit "Extract Shaders" afterwards.
    val pickDllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val destFile = File(context.filesDir, "Lossless.dll")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not open the selected file")
                dllPath = destFile.absolutePath
                setupMessage = ""
            } catch (e: Exception) {
                setupMessage = "Failed to import DLL: ${e.message}"
                setupPhase = SetupPhase.ERROR
            }
        }
    }

    val benchState by remember { derivedStateOf { BenchmarkController.currentState() } }

    NavHost(
        navController = navController,
        startDestination = NavRoute.Dashboard.route,
        modifier = modifier,
    ) {
        composable(NavRoute.Dashboard.route) {
            DashboardScreen(
                config = config,
                isSessionActive = isSessionActive,
                generatedFps = generatedFps,
                realFps = realFps,
                latencyMs = latencyMs,
                postedFps = postedFps,
                onToggleSession = { isSessionActive = !isSessionActive },
                onConfigChange = { config = it },
            )
        }
        composable(NavRoute.Settings.route) {
            SettingsScreen(
                config = config,
                onConfigChange = { config = it },
            )
        }
        composable(NavRoute.Benchmark.route) {
            BenchmarkScreen(
                benchState = benchState,
                onStartBenchmark = { /* service call */ },
                onCancelBenchmark = { BenchmarkController.cancel() },
                onAcknowledge = { BenchmarkController.acknowledge() },
            )
        }
        composable(NavRoute.Setup.route) {
            SetupScreen(
                phase = setupPhase,
                statusMessage = setupMessage,
                progressFraction = 0f,
                dllPath = dllPath,
                onPickDll = { pickDllLauncher.launch(arrayOf("*/*")) },
                onStartExtraction = {
                    if (dllPath.isNotEmpty()) {
                        setupPhase = SetupPhase.EXTRACTING
                        setupMessage = "Extracting shaders from Lossless.dll…"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val cacheDir = File(context.filesDir, "spirv").apply { mkdirs() }
                                val sha256 = sha256Of(File(dllPath))
                                val extractRc = NativeBridge.extractShaders(
                                    dllPath, sha256, cacheDir.absolutePath,
                                )
                                if (extractRc != NativeBridge.RC_OK) {
                                    setupMessage = "Shader extraction failed (code $extractRc)"
                                    setupPhase = SetupPhase.ERROR
                                    return@launch
                                }
                                setupPhase = SetupPhase.PROBING
                                val probeRc = NativeBridge.probeShaders(cacheDir.absolutePath)
                                if (probeRc == NativeBridge.RC_OK) {
                                    setupPhase = SetupPhase.DONE
                                } else {
                                    setupMessage = "Shader probe failed (code $probeRc)"
                                    setupPhase = SetupPhase.ERROR
                                }
                            } catch (e: Exception) {
                                setupMessage = "Extraction error: ${e.message}"
                                setupPhase = SetupPhase.ERROR
                            }
                        }
                    }
                },
            )
        }
    }
}

private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
