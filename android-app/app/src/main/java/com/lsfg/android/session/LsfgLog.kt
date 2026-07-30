package com.lsfg.android.session

import android.content.Context
import android.util.Log
import java.io.File

object LsfgLog {
    private const val TAG = "LsfgLog"

    fun init(ctx: Context) {
        val logFile = File(ctx.filesDir, "native_log.txt")
        Log.i(TAG, "Native log path: ${logFile.absolutePath}")
    }

    fun nativeLogFile(ctx: Context): File = File(ctx.filesDir, "native_log.txt")
}
