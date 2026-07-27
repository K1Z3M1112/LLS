package com.lsfg.android.benchmark

/**
 * A single telemetry snapshot taken during a benchmark run.
 * All counters are cumulative from the start of the run (base-subtracted by BenchmarkRunner).
 */
data class BenchmarkSample(
    /** Wall-clock milliseconds since the start of this run's sampling window. */
    val elapsedMs: Long,
    val uniqueCaptures: Long,
    val generatedFrames: Long,
    val postedFrames: Long,
    /** Recent post-to-post intervals in nanoseconds, newest last. May be empty. */
    val recentIntervalsNs: LongArray,
    /**
     * Native profile window snapshot: [copyNs, presentNs, waitIdleNs, blitNs, totalNs, count].
     * Null if not available.
     */
    val profileWindow: LongArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BenchmarkSample) return false
        return elapsedMs == other.elapsedMs &&
            uniqueCaptures == other.uniqueCaptures &&
            generatedFrames == other.generatedFrames &&
            postedFrames == other.postedFrames &&
            recentIntervalsNs.contentEquals(other.recentIntervalsNs) &&
            (profileWindow?.contentEquals(other.profileWindow ?: LongArray(0)) != false)
    }

    override fun hashCode(): Int {
        var result = elapsedMs.hashCode()
        result = 31 * result + uniqueCaptures.hashCode()
        result = 31 * result + generatedFrames.hashCode()
        result = 31 * result + postedFrames.hashCode()
        result = 31 * result + recentIntervalsNs.contentHashCode()
        result = 31 * result + (profileWindow?.contentHashCode() ?: 0)
        return result
    }
}
