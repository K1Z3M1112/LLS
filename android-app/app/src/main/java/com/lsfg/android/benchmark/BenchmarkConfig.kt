package com.lsfg.android.benchmark

/**
 * Static benchmark configuration. The Modalita Benchmark runs three back-to-back
 * passes with these parameters held fixed across runs; only the LSFG multiplier
 * changes between runs.
 */
object BenchmarkConfig {
    /** Multipliers exercised across the benchmark, in run order. */
    val MULTIPLIERS: IntArray = intArrayOf(2, 3, 4)

    val PRECISION_MODES: Array<PrecisionMode> = arrayOf(
        PrecisionMode.FP32,
        PrecisionMode.FP16,
    )

    /** Per-run sampling window length (seconds). */
    const val RUN_DURATION_SEC: Int = 60

    /** Sample collection interval (ms). 100 ms = 10 Hz. */
    const val SAMPLE_INTERVAL_MS: Long = 100L

    /**
     * FIX: Increased from 2 000 ms to 5 000 ms.
     * The original 2 s was too short on mid/low-end devices where Vulkan
     * pipeline creation + NNAPI model compilation can take 1-2 s by itself,
     * leaving no thermal settling time before sampling begins. 5 s gives
     * the native context reinit (destroyContext + initContext) and the first
     * few framegen windows time to stabilise before the measurement window.
     */
    const val WARMUP_MS: Long = 5_000L

    /** Fixed flow scale across runs. */
    const val FLOW_SCALE: Float = 0.70f

    /** Fixed performance mode (LSFG_3_1P). */
    const val PERFORMANCE_MODE: Boolean = true
}

enum class PrecisionMode(val nativeFp16Flag: Boolean, val label: String) {
    FP32(false, "FP32"),
    FP16(true, "FP16"),
}
