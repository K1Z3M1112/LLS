package com.lsfg.android.prefs

import android.content.Context
import android.content.SharedPreferences

data class LsfgConfig(
    val dllUri: String?,
    val dllDisplayName: String?,
    val shadersReady: Boolean,
    val lsfgEnabled: Boolean,
    val multiplier: Int,
    val flowScale: Float,
    val performanceMode: Boolean,
    val hdrMode: Boolean,
    val antiArtifacts: Boolean,
    /**
     * When true, the render loop loads the precompiled SPIR-V FP16 shader
     * variants from Lossless.dll (resource IDs 304..351) instead of
     * translating the DXBC FP32 set (255..302) via dxvk. Requires
     * `VK_KHR_shader_float16_int8` + `shaderFloat16` on the GPU. The UI
     * gates this via [NativeBridge.isFramegenFp16Supported] so unsupported
     * hardware never sees the toggle.
     */
    val framegenFp16: Boolean,
    val targetPackage: String?,
    val captureSource: CaptureSource,
    /**
     * Fraction of the display's native resolution to capture and render at,
     * from 1.0 (100%, native) down to 0.0 (0%, capture disabled — floor-clamped
     * to [MIN_RENDER_RESOLUTION_SCALE] everywhere it drives an actual buffer
     * size so we never allocate a 0x0 surface). Lower values shrink the
     * VirtualDisplay/ImageReader capture size and the native context's
     * width/height, cutting GPU cost across capture, frame-gen and every
     * post-processing pass — at the cost of a softer final image.
     */
    val renderResolutionScale: Float,
    val legalAccepted: Boolean,
    val fpsCounterEnabled: Boolean,
    val frameGraphEnabled: Boolean,
    val drawerEdge: DrawerEdge,
    val overlayMode: OverlayMode,
    val pacingPreset: PacingPreset,
    val vsyncAlignmentEnabled: Boolean,
    val vsyncRefreshOverride: VsyncRefreshOverride,
    val targetFpsCap: Int,
    val emaAlpha: Float,
    val outlierRatio: Float,
    val vsyncSlackMs: Float,
    val queueDepth: Int,
    val autoEnabledApps: Set<String>,
    val trustedOverlay: Boolean,
    /**
     * Opt-in fallback: when true and [trustedOverlay] is active, callers may use
     * [com.lsfg.android.session.LsfgAccessibilityService.forwardTap] /
     * `forwardSwipe` to synthesize touches on strict devices where even the
     * trusted-overlay pass-through path drops input. Off by default — see the
     * class doc on [com.lsfg.android.session.LsfgAccessibilityService] for why
     * this must stay opt-in rather than always-on.
     */
    val gestureForwardingEnabled: Boolean,
)

enum class DrawerEdge(val prefValue: String) {
    LEFT("left"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        fun fromPref(value: String?): DrawerEdge =
            values().firstOrNull { it.prefValue == value } ?: RIGHT
    }
}

/**
 * In-game settings overlay entry affordance.
 *
 *  - [ICON_BUTTON] (default): a small draggable circular icon floats on top of the
 *    target app. Tapping it opens the same settings panel from the chosen edge.
 *  - [DRAWER]: legacy edge-swipe handle. Reliable on most devices but a few OEM
 *    skins clip TYPE_APPLICATION_OVERLAY edges so the user can end up unable to
 *    drag the drawer back closed — see the warning shown in the UI.
 */
enum class OverlayMode(val prefValue: String) {
    ICON_BUTTON("icon_button"),
    DRAWER("drawer");

    companion object {
        fun fromPref(value: String?): OverlayMode =
            values().firstOrNull { it.prefValue == value } ?: ICON_BUTTON
    }
}

enum class CaptureSource(val prefValue: String) {
    MEDIA_PROJECTION("media_projection"),
    SHIZUKU("shizuku"),
    ROOT("root");

    companion object {
        fun fromPref(value: String?): CaptureSource =
            values().firstOrNull { it.prefValue == value } ?: MEDIA_PROJECTION
    }
}

enum class PacingPreset(val prefValue: String) {
    SMOOTH("smooth"),
    BALANCED("balanced"),
    LOW_LATENCY("low_latency"),
    CUSTOM("custom");

    companion object {
        fun fromPref(value: String?): PacingPreset =
            values().firstOrNull { it.prefValue == value } ?: BALANCED
    }
}

enum class VsyncRefreshOverride(val prefValue: String, val hz: Int) {
    AUTO("auto", 0),
    HZ_60("60", 60),
    HZ_90("90", 90),
    HZ_120("120", 120),
    HZ_144("144", 144);

    companion object {
        fun fromPref(value: String?): VsyncRefreshOverride =
            values().firstOrNull { it.prefValue == value } ?: AUTO
    }
}

object PacingDefaults {
    const val EMA_ALPHA: Float = 0.125f
    const val OUTLIER_RATIO: Float = 4.0f
    const val VSYNC_SLACK_MS: Float = 2.0f
    const val QUEUE_DEPTH: Int = 4
    const val TARGET_FPS_CAP: Int = 0
    const val VSYNC_ALIGNMENT: Boolean = true

    data class Params(val emaAlpha: Float, val outlierRatio: Float, val vsyncSlackMs: Float, val queueDepth: Int)

    fun forPreset(preset: PacingPreset, custom: Params): Params = when (preset) {
        PacingPreset.SMOOTH -> Params(0.08f, 6.0f, 3.0f, 6)
        PacingPreset.BALANCED -> Params(EMA_ALPHA, OUTLIER_RATIO, VSYNC_SLACK_MS, QUEUE_DEPTH)
        PacingPreset.LOW_LATENCY -> Params(0.2f, 3.0f, 1.5f, 2)
        PacingPreset.CUSTOM -> custom
    }
}

class LsfgPreferences(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): LsfgConfig = LsfgConfig(
        dllUri = prefs.getString(KEY_DLL_URI, null),
        dllDisplayName = prefs.getString(KEY_DLL_NAME, null),
        shadersReady = prefs.getBoolean(KEY_SHADERS_READY, false),
        lsfgEnabled = prefs.getBoolean(KEY_LSFG_ENABLED, true),
        multiplier = prefs.getInt(KEY_MULTIPLIER, 2).coerceIn(2, 8),
        flowScale = prefs.getFloat(KEY_FLOW_SCALE, 1.0f).coerceIn(0.25f, 1.0f),
        performanceMode = prefs.getBoolean(KEY_PERF, true),
        hdrMode = prefs.getBoolean(KEY_HDR, false),
        antiArtifacts = prefs.getBoolean(KEY_ANTI_ARTIFACTS, false),
        framegenFp16 = prefs.getBoolean(KEY_FRAMEGEN_FP16, false),
        targetPackage = prefs.getString(KEY_TARGET, null),
        captureSource = CaptureSource.fromPref(prefs.getString(KEY_CAPTURE_SOURCE, null)),
        renderResolutionScale = prefs.getFloat(KEY_RENDER_RESOLUTION_SCALE, 1.0f).coerceIn(0.0f, 1.0f),
        legalAccepted = prefs.getBoolean(KEY_LEGAL, false),
        fpsCounterEnabled = prefs.getBoolean(KEY_FPS_COUNTER, false),
        frameGraphEnabled = prefs.getBoolean(KEY_FRAME_GRAPH, false),
        drawerEdge = DrawerEdge.fromPref(prefs.getString(KEY_DRAWER_EDGE, null)),
        overlayMode = OverlayMode.fromPref(prefs.getString(KEY_OVERLAY_MODE, null)),
        pacingPreset = PacingPreset.fromPref(prefs.getString(KEY_PACING_PRESET, null)),
        vsyncAlignmentEnabled = prefs.getBoolean(KEY_VSYNC_ALIGN, PacingDefaults.VSYNC_ALIGNMENT),
        vsyncRefreshOverride = VsyncRefreshOverride.fromPref(prefs.getString(KEY_VSYNC_OVERRIDE, null)),
        targetFpsCap = prefs.getInt(KEY_TARGET_FPS_CAP, PacingDefaults.TARGET_FPS_CAP).coerceIn(0, 240),
        emaAlpha = prefs.getFloat(KEY_EMA_ALPHA, PacingDefaults.EMA_ALPHA).coerceIn(0.05f, 0.5f),
        outlierRatio = prefs.getFloat(KEY_OUTLIER_RATIO, PacingDefaults.OUTLIER_RATIO).coerceIn(2.0f, 8.0f),
        vsyncSlackMs = prefs.getFloat(KEY_VSYNC_SLACK_MS, PacingDefaults.VSYNC_SLACK_MS).coerceIn(1.0f, 5.0f),
        queueDepth = prefs.getInt(KEY_QUEUE_DEPTH, PacingDefaults.QUEUE_DEPTH).coerceIn(2, 6),
        autoEnabledApps = decodeAutoEnabledApps(prefs.getString(KEY_AUTO_ENABLED_APPS, null)),
        trustedOverlay = prefs.getBoolean(KEY_TRUSTED_OVERLAY, false),
        gestureForwardingEnabled = prefs.getBoolean(KEY_GESTURE_FORWARDING, false),
    )

    fun getAutoEnabledApps(): Set<String> =
        decodeAutoEnabledApps(prefs.getString(KEY_AUTO_ENABLED_APPS, null))

    fun setAutoEnabledApps(value: Set<String>) {
        prefs.edit()
            .putString(KEY_AUTO_ENABLED_APPS, value.joinToString("\n"))
            .apply()
    }

    fun setDll(uri: String, displayName: String) = prefs.edit()
        .putString(KEY_DLL_URI, uri)
        .putString(KEY_DLL_NAME, displayName)
        .putBoolean(KEY_SHADERS_READY, false)
        .apply()

    fun setShadersReady(ready: Boolean) = prefs.edit()
        .putBoolean(KEY_SHADERS_READY, ready)
        .apply()

    fun setLsfgEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_LSFG_ENABLED, value).apply()
    fun setMultiplier(value: Int) = prefs.edit().putInt(KEY_MULTIPLIER, value).apply()
    fun setFlowScale(value: Float) = prefs.edit().putFloat(KEY_FLOW_SCALE, value).apply()
    fun setPerformance(value: Boolean) = prefs.edit().putBoolean(KEY_PERF, value).apply()
    fun setHdr(value: Boolean) = prefs.edit().putBoolean(KEY_HDR, value).apply()
    fun setAntiArtifacts(value: Boolean) = prefs.edit().putBoolean(KEY_ANTI_ARTIFACTS, value).apply()
    fun setFramegenFp16(value: Boolean) = prefs.edit().putBoolean(KEY_FRAMEGEN_FP16, value).apply()
    fun setTargetPackage(pkg: String?) = prefs.edit().putString(KEY_TARGET, pkg).apply()
    fun setCaptureSource(value: CaptureSource) = prefs.edit()
        .putString(KEY_CAPTURE_SOURCE, value.prefValue)
        .apply()
    fun setRenderResolutionScale(value: Float) = prefs.edit()
        .putFloat(KEY_RENDER_RESOLUTION_SCALE, value.coerceIn(0.0f, 1.0f))
        .apply()
    fun setLegalAccepted(value: Boolean) = prefs.edit().putBoolean(KEY_LEGAL, value).apply()
    fun isTutorialPromptShown(): Boolean = prefs.getBoolean(KEY_TUTORIAL_PROMPT_SHOWN, false)
    fun setTutorialPromptShown(value: Boolean) =
        prefs.edit().putBoolean(KEY_TUTORIAL_PROMPT_SHOWN, value).apply()
    fun setFpsCounterEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_FPS_COUNTER, value).apply()
    fun setFrameGraphEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_FRAME_GRAPH, value).apply()
    fun setDrawerEdge(value: DrawerEdge) = prefs.edit()
        .putString(KEY_DRAWER_EDGE, value.prefValue)
        .apply()
    fun setOverlayMode(value: OverlayMode) = prefs.edit()
        .putString(KEY_OVERLAY_MODE, value.prefValue)
        .apply()

    fun setPacingPreset(value: PacingPreset) = prefs.edit()
        .putString(KEY_PACING_PRESET, value.prefValue)
        .apply()
    fun setVsyncAlignmentEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_VSYNC_ALIGN, value).apply()
    fun setVsyncRefreshOverride(value: VsyncRefreshOverride) = prefs.edit()
        .putString(KEY_VSYNC_OVERRIDE, value.prefValue)
        .apply()
    fun setTargetFpsCap(value: Int) = prefs.edit().putInt(KEY_TARGET_FPS_CAP, value.coerceIn(0, 240)).apply()
    fun setEmaAlpha(value: Float) = prefs.edit().putFloat(KEY_EMA_ALPHA, value.coerceIn(0.05f, 0.5f)).apply()
    fun setOutlierRatio(value: Float) = prefs.edit().putFloat(KEY_OUTLIER_RATIO, value.coerceIn(2.0f, 8.0f)).apply()
    fun setVsyncSlackMs(value: Float) = prefs.edit().putFloat(KEY_VSYNC_SLACK_MS, value.coerceIn(1.0f, 5.0f)).apply()
    fun setQueueDepth(value: Int) = prefs.edit().putInt(KEY_QUEUE_DEPTH, value.coerceIn(2, 6)).apply()

    fun setTrustedOverlay(value: Boolean) = prefs.edit().putBoolean(KEY_TRUSTED_OVERLAY, value).apply()

    fun setGestureForwardingEnabled(value: Boolean) =
        prefs.edit().putBoolean(KEY_GESTURE_FORWARDING, value).apply()

    companion object {
        private const val FILE = "lsfg_prefs"
        private const val KEY_DLL_URI = "dll_uri"
        private const val KEY_DLL_NAME = "dll_name"
        private const val KEY_SHADERS_READY = "shaders_ready"
        private const val KEY_LSFG_ENABLED = "lsfg_enabled"
        private const val KEY_MULTIPLIER = "multiplier"
        private const val KEY_FLOW_SCALE = "flow_scale"
        private const val KEY_PERF = "performance"
        private const val KEY_HDR = "hdr"
        private const val KEY_ANTI_ARTIFACTS = "anti_artifacts"
        private const val KEY_FRAMEGEN_FP16 = "framegen_fp16"
        private const val KEY_TARGET = "target_pkg"
        private const val KEY_CAPTURE_SOURCE = "capture_source"
        private const val KEY_RENDER_RESOLUTION_SCALE = "render_resolution_scale"
        /**
         * Never let the *effective* capture/render size collapse to 0 pixels even
         * if the user drags the slider all the way to 0%. Applied only where the
         * scale multiplies an actual buffer dimension (VirtualDisplay/ImageReader
         * size, native context width/height) — the stored preference and the UI
         * slider itself still range down to a true 0.0.
         */
        const val MIN_RENDER_RESOLUTION_SCALE = 0.1f
        private const val KEY_LEGAL = "legal_accepted"
        private const val KEY_TUTORIAL_PROMPT_SHOWN = "tutorial_prompt_shown"
        private const val KEY_FPS_COUNTER = "fps_counter"
        private const val KEY_FRAME_GRAPH = "frame_graph"
        private const val KEY_DRAWER_EDGE = "drawer_edge"
        private const val KEY_OVERLAY_MODE = "overlay_mode"
        private const val KEY_PACING_PRESET = "pacing_preset"
        private const val KEY_VSYNC_ALIGN = "vsync_alignment"
        private const val KEY_VSYNC_OVERRIDE = "vsync_refresh_override"
        private const val KEY_TARGET_FPS_CAP = "target_fps_cap"
        private const val KEY_EMA_ALPHA = "pacing_ema_alpha"
        private const val KEY_OUTLIER_RATIO = "pacing_outlier_ratio"
        private const val KEY_VSYNC_SLACK_MS = "pacing_vsync_slack_ms"
        private const val KEY_QUEUE_DEPTH = "pacing_queue_depth"
        private const val KEY_AUTO_ENABLED_APPS = "auto_enabled_apps"
        private const val KEY_TRUSTED_OVERLAY = "trusted_overlay"
        private const val KEY_GESTURE_FORWARDING = "gesture_forwarding_enabled"

        private fun decodeAutoEnabledApps(raw: String?): Set<String> {
            if (raw.isNullOrEmpty()) return emptySet()
            return raw.split('\n').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        }
    }
}
