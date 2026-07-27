package com.lsfg.android.benchmark

import com.lsfg.android.session.NativeBridge
import kotlin.math.sqrt

/**
 * Stateless utility that polls [NativeBridge] counters at a fixed cadence
 * and aggregates them into a [BenchmarkRunResult]. Does not orchestrate
 * session lifecycle — the caller is responsible for ensuring frame-gen is
 * active and stable (warmup) before invoking [collect].
 *
 * Designed to be called from a worker thread (it sleeps between samples).
 * Returns when [durationMs] has elapsed or the [shouldStop] predicate fires.
 */
object BenchmarkRunner {

    private const val INTERVAL_BUF_SIZE = 128

    private const val VSYNC_SLACK_NS: Long = 2_000_000L
    private const val STALL_MULTIPLIER: Int = 2

    fun collect(
        multiplier: Int,
        flowScale: Float,
        performanceMode: Boolean,
        framegenFp16: Boolean,
        durationMs: Long,
        sampleIntervalMs: Long,
        nominalVsyncPeriodNs: Long,
        shouldStop: () -> Boolean = { false },
    ): BenchmarkRunResult {
        val startMs = System.currentTimeMillis()
        val samples = ArrayList<BenchmarkSample>(
            ((durationMs / sampleIntervalMs) + 4).toInt()
        )

        val intervalBuf = LongArray(INTERVAL_BUF_SIZE)
        val profileBuf = LongArray(6)

        val baseUnique = safeCall { NativeBridge.getUniqueCaptureCount() } ?: 0L
        val baseGenerated = safeCall { NativeBridge.getGeneratedFrameCount() } ?: 0L
        val basePosted = safeCall { NativeBridge.getPostedFrameCount() } ?: 0L

        while (true) {
            val nowMs = System.currentTimeMillis()
            val elapsed = nowMs - startMs
            if (elapsed >= durationMs || shouldStop()) break

            val unique = (safeCall { NativeBridge.getUniqueCaptureCount() } ?: 0L) - baseUnique
            val gen = (safeCall { NativeBridge.getGeneratedFrameCount() } ?: 0L) - baseGenerated
            val posted = (safeCall { NativeBridge.getPostedFrameCount() } ?: 0L) - basePosted

            val nIntervals = safeCall { NativeBridge.getRecentPostIntervalsNs(intervalBuf) } ?: 0
            val intervals = if (nIntervals > 0) intervalBuf.copyOf(nIntervals) else LongArray(0)

            val nProf = safeCall { NativeBridge.getProfileWindowNs(profileBuf) } ?: 0
            val profile = if (nProf >= 6) profileBuf.copyOf(6) else null

            samples.add(
                BenchmarkSample(
                    elapsedMs = elapsed,
                    uniqueCaptures = unique,
                    generatedFrames = gen,
                    postedFrames = posted,
                    recentIntervalsNs = intervals,
                    profileWindow = profile,
                )
            )

            try {
                Thread.sleep(sampleIntervalMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        val endMs = System.currentTimeMillis()
        val runMs = endMs - startMs

        return aggregate(
            multiplier = multiplier,
            flowScale = flowScale,
            performanceMode = performanceMode,
            framegenFp16 = framegenFp16,
            runDurationMs = runMs,
            samples = samples,
            nominalVsyncPeriodNs = nominalVsyncPeriodNs,
        )
    }

    private fun aggregate(
        multiplier: Int,
        flowScale: Float,
        performanceMode: Boolean,
        framegenFp16: Boolean,
        runDurationMs: Long,
        samples: List<BenchmarkSample>,
        nominalVsyncPeriodNs: Long,
    ): BenchmarkRunResult {
        val last = samples.lastOrNull()
        val totalUnique = last?.uniqueCaptures ?: 0L
        val totalGen = last?.generatedFrames ?: 0L
        val totalPosted = last?.postedFrames ?: 0L
        val seconds = runDurationMs / 1000.0

        val intervalsNs = last?.recentIntervalsNs ?: LongArray(0)
        val pacingStats = if (intervalsNs.isNotEmpty()) computePacingStats(intervalsNs) else null

        val profile = last?.profileWindow?.let { p ->
            val n = p[5].toDouble().coerceAtLeast(1.0)
            ProfileStats(
                samples = p[5],
                copyMs = (p[0] / n) / 1_000_000.0,
                presentMs = (p[1] / n) / 1_000_000.0,
                waitIdleMs = (p[2] / n) / 1_000_000.0,
                blitMs = (p[3] / n) / 1_000_000.0,
                totalMs = (p[4] / n) / 1_000_000.0,
            )
        }

        var aligned = 0
        var stalls = 0
        if (nominalVsyncPeriodNs > 0 && intervalsNs.isNotEmpty()) {
            val stallThreshold = nominalVsyncPeriodNs.toLong() * STALL_MULTIPLIER
            for (intervalNs in intervalsNs) {
                if (kotlin.math.abs(intervalNs - nominalVsyncPeriodNs) <= VSYNC_SLACK_NS) {
                    aligned++
                }
                if (intervalNs > stallThreshold) {
                    stalls++
                }
            }
        } else if (intervalsNs.isNotEmpty()) {
            val sorted = intervalsNs.sortedArray()
            val median = sorted[sorted.size / 2]
            val threshold = median * STALL_MULTIPLIER
            stalls = intervalsNs.count { it > threshold }
        }

        val vsyncAlignPct = if (nominalVsyncPeriodNs > 0 && intervalsNs.isNotEmpty()) {
            (aligned.toDouble() * 100.0 / intervalsNs.size)
        } else null

        return BenchmarkRunResult(
            multiplier = multiplier,
            flowScale = flowScale,
            performanceMode = performanceMode,
            framegenFp16 = framegenFp16,
            runDurationMs = runDurationMs,
            totalUniqueCaptures = totalUnique,
            totalGeneratedFrames = totalGen,
            totalPostedFrames = totalPosted,
            realFps = if (seconds > 0) totalUnique / seconds else 0.0,
            generatedFps = if (seconds > 0) totalGen / seconds else 0.0,
            postedFps = if (seconds > 0) totalPosted / seconds else 0.0,
            pacingMs = pacingStats,
            profile = profile,
            vsyncAlignmentPercent = vsyncAlignPct,
            stallCount = stalls,
        )
    }

    private fun computePacingStats(intervalsNs: LongArray): PacingStats {
        val ms = DoubleArray(intervalsNs.size) { intervalsNs[it] / 1_000_000.0 }
        val sorted = ms.copyOf().also { it.sort() }
        val n = sorted.size

        val mean = ms.sum() / n
        var sqSum = 0.0
        for (v in ms) {
            val d = v - mean
            sqSum += d * d
        }
        val stddev = sqrt(sqSum / n)
        val jitter = if (mean > 0) stddev / mean else 0.0

        // FIX: use true median (average of two middle values for even n)
        // instead of always taking the upper-middle element.
        val p50 = if (n % 2 == 0) {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        } else {
            sorted[n / 2]
        }

        return PacingStats(
            sampleCount = n,
            minMs = sorted.first(),
            p50Ms = p50,
            p90Ms = sorted[((n - 1) * 0.90).toInt().coerceIn(0, n - 1)],
            p99Ms = sorted[((n - 1) * 0.99).toInt().coerceIn(0, n - 1)],
            maxMs = sorted.last(),
            meanMs = mean,
            stddevMs = stddev,
            jitterRatio = jitter,
        )
    }

    private inline fun <T> safeCall(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }
}
