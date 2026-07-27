package com.lsfg.android.session

/**
 * JNI bridge to liblsfg.so.
 * Method signatures must match the `extern "C" JNIEXPORT` declarations in lsfg_jni.cpp.
 */
object NativeBridge {

    init {
        System.loadLibrary("lsfg")
    }

    // --- Version / crash reporter -------------------------------------------
    external fun nativeVersion(): String
    external fun initCrashReporter(crashPath: String, logPath: String)

    // --- Phase 3: DLL → SPIR-V extraction -----------------------------------
    /** Returns 0 on success, negative on error (see kErr* constants in android_shader_loader.hpp). */
    external fun extractShaders(dllPath: String, dllSha256: String, cacheDir: String): Int

    // --- Phase 4: shader probe -----------------------------------------------
    /** Validates every cached SPIR-V blob with vkCreateShaderModule. */
    external fun probeShaders(cacheDir: String): Int

    // --- Phase 5: render loop lifecycle ---------------------------------------
    external fun initContext(
        cacheDir: String,
        width: Int, height: Int,
        multiplier: Int, flowScale: Float,
        performance: Boolean, hdr: Boolean,
        antiArtifacts: Boolean, framegenFp16: Boolean,
        npuPostProcessing: Boolean, npuPreset: Int,
        npuUpscaleFactor: Int, npuAmount: Float,
        npuRadius: Float, npuThreshold: Float, npuFp16: Boolean,
        cpuPostProcessing: Boolean, cpuPreset: Int,
        cpuStrength: Float, cpuSaturation: Float,
        cpuVibrance: Float, cpuVignette: Float,
        gpuPostProcessing: Boolean, gpuStage: Int,
        gpuMethod: Int, gpuUpscaleFactor: Float,
        gpuSharpness: Float, gpuStrength: Float,
        targetFpsCap: Int, emaAlpha: Float,
        outlierRatio: Float, vsyncSlackMs: Float,
        queueDepth: Int,
    ): Int

    external fun setOutputSurface(surface: Any?, w: Int, h: Int)
    external fun pushFrame(hardwareBuffer: Any, timestampNs: Long)
    external fun destroyContext()

    // --- Live stats ----------------------------------------------------------
    external fun getGeneratedFrameCount(): Long
    external fun getPostedFrameCount(): Long
    external fun getUniqueCaptureCount(): Long
    external fun getAverageQueueMs(): Double
    external fun getAverageLatencyMs(): Double
    external fun getRecentPostIntervalsNs(outArray: LongArray): Int
    external fun getProfileWindowNs(outArray: LongArray): Int

    // --- Hot-path controls ---------------------------------------------------
    external fun setBypass(bypass: Boolean)
    external fun setAntiArtifacts(enabled: Boolean)
    external fun setVsyncPeriodNs(periodNs: Long)
    external fun setPacingParams(
        targetFpsCap: Int,
        emaAlpha: Float,
        outlierRatio: Float,
        vsyncSlackMs: Float,
        queueDepth: Int,
    )
    external fun setShizukuTimingEnabled(enabled: Boolean)
    external fun reportShizukuTiming(
        timestampNs: Long,
        frameTimeNs: Long,
        pacingJitterNs: Long,
    )

    // --- Device capability probes -------------------------------------------
    external fun isNpuAvailable(): Boolean
    external fun isFramegenFp16Supported(cacheDir: String): Boolean
    external fun getNpuSummary(): String

    // Return codes (mirror kErr* in android_shader_loader.hpp)
    const val RC_OK                 = 0
    const val RC_ERR_DLL_UNREADABLE = -1
    const val RC_ERR_MISSING_RES    = -2
    const val RC_ERR_TRANSLATION    = -3
    const val RC_ERR_WRITE          = -4
    const val RC_PROBE_NO_VULKAN    = -10
    const val RC_PROBE_MISSING_SPV  = -11
    const val RC_PROBE_REJECTED     = -12
}
