package com.firstt175.deepdrop

import android.app.Application
import com.firstt175.deepdrop.session.AppIntegrity
import com.firstt175.deepdrop.session.CrashReporter
import com.firstt175.deepdrop.session.LsfgLog

class LsfgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install the crash reporter as early as possible so even a crash during
        // the first JNI load attempt gets captured (NativeBridge static init
        // already loads the .so; the call below only configures the handler).
        CrashReporter.install(this)
        LsfgLog.init(this)

        // Informational only — see AppIntegrity's kdoc. Recorded to the local
        // log so it's visible in an exported diagnostics report if a user
        // ever needs support with a copy that turns out not to be official;
        // this never alters app behavior.
        when (AppIntegrity.check(this)) {
            AppIntegrity.Result.UNOFFICIAL ->
                LsfgLog.w("AppIntegrity", "Running build's signature does not match the official release key")
            AppIntegrity.Result.OFFICIAL ->
                LsfgLog.i("AppIntegrity", "Build signature verified as official")
            AppIntegrity.Result.NOT_CONFIGURED -> Unit
        }
    }
}
