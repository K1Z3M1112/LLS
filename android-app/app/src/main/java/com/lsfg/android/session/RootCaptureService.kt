package com.lsfg.android.session

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * libsu RootService subclass for root-based screen capture.
 * Full implementation in production codebase.
 */
class RootCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
