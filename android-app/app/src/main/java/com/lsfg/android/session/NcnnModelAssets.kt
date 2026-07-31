package com.lsfg.android.session

import android.content.Context
import java.io.File

/**
 * Extracts the bundled `frame_interpolator_v2` ncnn model files (assets/models/*) to app-private
 * internal storage. Native code (NcnnFG::initialize, see ncnn_framegen.cpp) needs real filesystem
 * paths — it doesn't know how to read out of the APK's asset stream — so this is a one-time-ish
 * copy step run before [NativeBridge.initContext] whenever the ncnn engine is selected.
 *
 * Idempotent: skips the copy when a destination file already exists with the exact byte size of
 * the source asset, which is enough to detect "already extracted" without hashing on every
 * session start. If the app is ever updated with different model files under the same names,
 * bump [MODEL_VERSION] to force a fresh copy.
 */
object NcnnModelAssets {
    private const val MODEL_VERSION = 1
    private const val ASSET_DIR = "models"

    data class Paths(val paramPath: String, val binPath: String)

    /**
     * @param useFp16 selects `frame_interpolator_v2_fp16_ncnn.*` vs the `fp32` variant.
     * @return absolute filesystem paths to the extracted .param/.bin pair.
     */
    fun ensureExtracted(ctx: Context, useFp16: Boolean): Paths {
        val outDir = File(ctx.filesDir, "ncnn_models/v$MODEL_VERSION").apply { mkdirs() }
        val suffix = if (useFp16) "fp16" else "fp32"
        val paramName = "frame_interpolator_v2_${suffix}_ncnn.param"
        val binName = "frame_interpolator_v2_${suffix}_ncnn.bin"

        val paramFile = File(outDir, paramName)
        val binFile = File(outDir, binName)
        copyAssetIfNeeded(ctx, "$ASSET_DIR/$paramName", paramFile)
        copyAssetIfNeeded(ctx, "$ASSET_DIR/$binName", binFile)

        return Paths(paramFile.absolutePath, binFile.absolutePath)
    }

    private fun copyAssetIfNeeded(ctx: Context, assetPath: String, dest: File) {
        val assetSize = ctx.assets.openFd(assetPath).use { it.length }
        if (dest.exists() && dest.length() == assetSize) return

        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        ctx.assets.open(assetPath).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        // Atomic-ish swap so a crash mid-copy never leaves a truncated file that
        // passes the size-check above on the next launch.
        tmp.renameTo(dest)
    }
}
