package com.lsfg.android.benchmark

data class BenchmarkRunResult(
    val multiplier: Int,
    val flowScale: Float,
    val performanceMode: Boolean,
    val framegenFp16: Boolean,
    val runDurationMs: Long,
    val totalUniqueCaptures: Long,
    val totalGeneratedFrames: Long,
    val totalPostedFrames: Long,
    val realFps: Double,
    val generatedFps: Double,
    val postedFps: Double,
    val pacingMs: PacingStats?,
    val profile: ProfileStats?,
    val vsyncAlignmentPercent: Double?,
    val stallCount: Int,
)

data class PacingStats(
    val sampleCount: Int,
    val minMs: Double,
    val p50Ms: Double,
    val p90Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val meanMs: Double,
    val stddevMs: Double,
    val jitterRatio: Double,
)

data class ProfileStats(
    val samples: Long,
    val copyMs: Double,
    val presentMs: Double,
    val waitIdleMs: Double,
    val blitMs: Double,
    val totalMs: Double,
)
