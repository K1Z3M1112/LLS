package com.lsfg.android.prefs

import android.content.Context
import android.content.SharedPreferences

data class LsfgConfig(
    val multiplier: Int = 2,
    val flowScale: Float = 0.70f,
    val performanceMode: Boolean = true,
    val hdr: Boolean = false,
    val antiArtifacts: Boolean = false,
    val framegenFp16: Boolean = false,
    // NPU
    val npuPostProcessingEnabled: Boolean = false,
    val npuPostProcessingPreset: NpuPostProcessingPreset = NpuPostProcessingPreset.OFF,
    val npuUpscaleFactor: Int = 1,
    val npuAmount: Float = 0.5f,
    val npuRadius: Float = 1.0f,
    val npuThreshold: Float = 0.0f,
    val npuFp16: Boolean = true,
    // CPU
    val cpuPostProcessingEnabled: Boolean = false,
    val cpuPostProcessingPreset: CpuPostProcessingPreset = CpuPostProcessingPreset.OFF,
    val cpuStrength: Float = 0.5f,
    val cpuSaturation: Float = 0.5f,
    val cpuVibrance: Float = 0.0f,
    val cpuVignette: Float = 0.0f,
    // GPU
    val gpuPostProcessingEnabled: Boolean = false,
    val gpuStage: Int = 1,
    val gpuMethod: Int = 0,
    val gpuUpscaleFactor: Float = 1.0f,
    val gpuSharpness: Float = 0.5f,
    val gpuStrength: Float = 0.5f,
    // Pacing
    val targetFpsCap: Int = 0,
    val emaAlpha: Float = 0.125f,
    val outlierRatio: Float = 4.0f,
    val vsyncSlackMs: Float = 2.0f,
    val queueDepth: Int = 4,
    // Misc
    val vsyncRefreshOverride: VsyncRefreshOverride = VsyncRefreshOverride.AUTO,
    val showHud: Boolean = true,
    val bypassMode: Boolean = false,
    val shizukuTimingEnabled: Boolean = false,
    val dllPath: String = "",
    val dllSha256: String = "",
)

class LsfgPreferences(ctx: Context) {
    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("lsfg_prefs", Context.MODE_PRIVATE)

    fun load(): LsfgConfig = LsfgConfig(
        multiplier              = prefs.getInt("multiplier", 2),
        flowScale               = prefs.getFloat("flow_scale", 0.70f),
        performanceMode         = prefs.getBoolean("performance_mode", true),
        hdr                     = prefs.getBoolean("hdr", false),
        antiArtifacts           = prefs.getBoolean("anti_artifacts", false),
        framegenFp16            = prefs.getBoolean("framegen_fp16", false),
        npuPostProcessingEnabled= prefs.getBoolean("npu_enabled", false),
        npuPostProcessingPreset = NpuPostProcessingPreset.entries.getOrNull(
            prefs.getInt("npu_preset", 0)) ?: NpuPostProcessingPreset.OFF,
        npuUpscaleFactor        = prefs.getInt("npu_upscale", 1),
        npuAmount               = prefs.getFloat("npu_amount", 0.5f),
        npuRadius               = prefs.getFloat("npu_radius", 1.0f),
        npuThreshold            = prefs.getFloat("npu_threshold", 0.0f),
        npuFp16                 = prefs.getBoolean("npu_fp16", true),
        cpuPostProcessingEnabled= prefs.getBoolean("cpu_enabled", false),
        cpuPostProcessingPreset = CpuPostProcessingPreset.entries.getOrNull(
            prefs.getInt("cpu_preset", 0)) ?: CpuPostProcessingPreset.OFF,
        cpuStrength             = prefs.getFloat("cpu_strength", 0.5f),
        cpuSaturation           = prefs.getFloat("cpu_saturation", 0.5f),
        cpuVibrance             = prefs.getFloat("cpu_vibrance", 0.0f),
        cpuVignette             = prefs.getFloat("cpu_vignette", 0.0f),
        gpuPostProcessingEnabled= prefs.getBoolean("gpu_enabled", false),
        gpuStage                = prefs.getInt("gpu_stage", 1),
        gpuMethod               = prefs.getInt("gpu_method", 0),
        gpuUpscaleFactor        = prefs.getFloat("gpu_upscale", 1.0f),
        gpuSharpness            = prefs.getFloat("gpu_sharpness", 0.5f),
        gpuStrength             = prefs.getFloat("gpu_strength", 0.5f),
        targetFpsCap            = prefs.getInt("target_fps_cap", 0),
        emaAlpha                = prefs.getFloat("ema_alpha", 0.125f),
        outlierRatio            = prefs.getFloat("outlier_ratio", 4.0f),
        vsyncSlackMs            = prefs.getFloat("vsync_slack_ms", 2.0f),
        queueDepth              = prefs.getInt("queue_depth", 4),
        vsyncRefreshOverride    = VsyncRefreshOverride.entries.getOrNull(
            prefs.getInt("vsync_override", 0)) ?: VsyncRefreshOverride.AUTO,
        showHud                 = prefs.getBoolean("show_hud", true),
        bypassMode              = prefs.getBoolean("bypass_mode", false),
        shizukuTimingEnabled    = prefs.getBoolean("shizuku_timing", false),
        dllPath                 = prefs.getString("dll_path", "") ?: "",
        dllSha256               = prefs.getString("dll_sha256", "") ?: "",
    )

    fun save(c: LsfgConfig) = prefs.edit().apply {
        putInt("multiplier", c.multiplier)
        putFloat("flow_scale", c.flowScale)
        putBoolean("performance_mode", c.performanceMode)
        putBoolean("hdr", c.hdr)
        putBoolean("anti_artifacts", c.antiArtifacts)
        putBoolean("framegen_fp16", c.framegenFp16)
        putBoolean("npu_enabled", c.npuPostProcessingEnabled)
        putInt("npu_preset", c.npuPostProcessingPreset.nativeValue)
        putInt("npu_upscale", c.npuUpscaleFactor)
        putFloat("npu_amount", c.npuAmount)
        putFloat("npu_radius", c.npuRadius)
        putFloat("npu_threshold", c.npuThreshold)
        putBoolean("npu_fp16", c.npuFp16)
        putBoolean("cpu_enabled", c.cpuPostProcessingEnabled)
        putInt("cpu_preset", c.cpuPostProcessingPreset.nativeValue)
        putFloat("cpu_strength", c.cpuStrength)
        putFloat("cpu_saturation", c.cpuSaturation)
        putFloat("cpu_vibrance", c.cpuVibrance)
        putFloat("cpu_vignette", c.cpuVignette)
        putBoolean("gpu_enabled", c.gpuPostProcessingEnabled)
        putInt("gpu_stage", c.gpuStage)
        putInt("gpu_method", c.gpuMethod)
        putFloat("gpu_upscale", c.gpuUpscaleFactor)
        putFloat("gpu_sharpness", c.gpuSharpness)
        putFloat("gpu_strength", c.gpuStrength)
        putInt("target_fps_cap", c.targetFpsCap)
        putFloat("ema_alpha", c.emaAlpha)
        putFloat("outlier_ratio", c.outlierRatio)
        putFloat("vsync_slack_ms", c.vsyncSlackMs)
        putInt("queue_depth", c.queueDepth)
        putInt("vsync_override", c.vsyncRefreshOverride.ordinal)
        putBoolean("show_hud", c.showHud)
        putBoolean("bypass_mode", c.bypassMode)
        putBoolean("shizuku_timing", c.shizukuTimingEnabled)
        putString("dll_path", c.dllPath)
        putString("dll_sha256", c.dllSha256)
    }.apply()

    // Convenience setters used by BenchmarkController
    fun setMultiplier(v: Int)                               = prefs.edit().putInt("multiplier", v).apply()
    fun setFlowScale(v: Float)                              = prefs.edit().putFloat("flow_scale", v).apply()
    fun setPerformance(v: Boolean)                          = prefs.edit().putBoolean("performance_mode", v).apply()
    fun setAntiArtifacts(v: Boolean)                        = prefs.edit().putBoolean("anti_artifacts", v).apply()
    fun setFramegenFp16(v: Boolean)                         = prefs.edit().putBoolean("framegen_fp16", v).apply()
    fun setNpuPostProcessingEnabled(v: Boolean)             = prefs.edit().putBoolean("npu_enabled", v).apply()
    fun setNpuPostProcessingPreset(v: NpuPostProcessingPreset) = prefs.edit().putInt("npu_preset", v.nativeValue).apply()
    fun setCpuPostProcessingEnabled(v: Boolean)             = prefs.edit().putBoolean("cpu_enabled", v).apply()
    fun setCpuPostProcessingPreset(v: CpuPostProcessingPreset) = prefs.edit().putInt("cpu_preset", v.nativeValue).apply()
    fun setGpuPostProcessingEnabled(v: Boolean)             = prefs.edit().putBoolean("gpu_enabled", v).apply()
    fun setVsyncRefreshOverride(v: VsyncRefreshOverride)    = prefs.edit().putInt("vsync_override", v.ordinal).apply()
    fun setDllPath(path: String, sha256: String) {
        prefs.edit().putString("dll_path", path).putString("dll_sha256", sha256).apply()
    }
}
