package com.lsfg.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lsfg.android.benchmark.BenchmarkController
import com.lsfg.android.prefs.LsfgConfig
import com.lsfg.android.ui.screens.*

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
                onPickDll = { /* SAF picker launch */ },
                onStartExtraction = { },
            )
        }
    }
}
