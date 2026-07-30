package com.lsfg.android

/**
 * Build-time feature flags. Toggle here (or via BuildConfig in build.gradle.kts)
 * to enable unreleased or experimental features during development.
 */
object FeatureFlags {
    /** Show image quality metrics in the HUD. Disabled until the quality
     *  estimator pipeline is stable on all target devices. */
    const val SHOW_IMAGE_QUALITY: Boolean = true

    /** Enable the NPU post-processing pipeline. Requires NNAPI + supported preset. */
    const val NPU_POST_PROCESSING: Boolean = true

    /** Enable the GPU (Vulkan compute) post-processing stage. */
    const val GPU_POST_PROCESSING: Boolean = true

    /** Show experimental FP16 frame-gen shader option in settings. Only visible
     *  when the device probe confirms shaderFloat16 support. */
    const val FP16_FRAMEGEN: Boolean = true

    /** Enable the root capture path (libsu RootService). */
    const val ROOT_CAPTURE: Boolean = true

    /** Enable the Shizuku capture path. */
    const val SHIZUKU_CAPTURE: Boolean = true

    /** Show the benchmark screen in the bottom navigation. */
    const val BENCHMARK_UI: Boolean = true
}
