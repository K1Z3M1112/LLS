package com.lsfg.android.session

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Main foreground service that holds the MediaProjection token and drives the
 * LSFG render loop. Full implementation in the production codebase; this stub
 * satisfies the AndroidManifest <service> declaration and the
 * [BenchmarkController.Hooks] interface so the benchmark UI compiles.
 */
class LsfgForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
