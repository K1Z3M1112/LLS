package com.lsfg.android.ui

import android.app.Activity
import android.os.Bundle

/**
 * Transparent trampoline activity that requests the MediaProjection consent
 * dialog without appearing in the recents screen. Finishes immediately after
 * forwarding the result to LsfgForegroundService.
 * Full implementation in production codebase.
 */
class ProjectionRequestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
