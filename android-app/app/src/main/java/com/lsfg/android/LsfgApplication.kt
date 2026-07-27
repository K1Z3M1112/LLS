package com.lsfg.android

import android.app.Application
import com.lsfg.android.session.CrashReporter
import com.lsfg.android.session.LsfgLog

class LsfgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install native crash reporter before any JNI calls so signal handlers
        // are active from the first dlopen onwards.
        CrashReporter.install(this)
        LsfgLog.init(this)
    }
}
