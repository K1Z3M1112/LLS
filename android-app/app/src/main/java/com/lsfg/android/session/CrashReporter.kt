package com.lsfg.android.session

import android.content.Context
import java.io.File

object CrashReporter {
    fun install(ctx: Context) {
        val filesDir = ctx.filesDir
        val crashFile = File(filesDir, "native_crash.txt").absolutePath
        val logFile   = File(filesDir, "native_log.txt").absolutePath
        runCatching { NativeBridge.initCrashReporter(crashFile, logFile) }
    }

    fun lastCrashFile(ctx: Context): File =
        File(ctx.filesDir, "native_crash.txt")
}
