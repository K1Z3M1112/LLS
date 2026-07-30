package com.lsfg.android

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class LsfgApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Copy bundled NCNN models from assets to internal storage
        // on first install / after an update (checks if asset changed).
        CoroutineScope(Dispatchers.IO).launch {
            copyNcnnModelsFromAssets()
        }
    }

    private fun copyNcnnModelsFromAssets() {
        val ncnnDir = File(filesDir, "ncnn")
        ncnnDir.mkdirs()

        val models = listOf(
            "frame_interpolator_v2_fp32.ncnn.param",
            "frame_interpolator_v2_fp32.ncnn.bin",
            "frame_interpolator_v2_fp16.ncnn.param",
            "frame_interpolator_v2_fp16.ncnn.bin",
        )

        for (name in models) {
            val dest = File(ncnnDir, name)
            try {
                assets.open("ncnn/$name").use { inp ->
                    // Overwrite only when size differs (cheap staleness check).
                    val assetSize = inp.available().toLong()
                    if (dest.exists() && dest.length() == assetSize) return@use
                    FileOutputStream(dest).use { out -> inp.copyTo(out) }
                    Log.i(TAG, "Copied asset ncnn/$name → ${dest.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy ncnn/$name: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "LsfgApplication"

        /** Absolute path to the bundled NCNN model directory inside internal storage. */
        fun ncnnModelDir(filesDir: File): File = File(filesDir, "ncnn")

        fun bundledParamPath(filesDir: File, precision: String): String =
            File(ncnnModelDir(filesDir),
                "frame_interpolator_v2_$precision.ncnn.param").absolutePath
    }
}
