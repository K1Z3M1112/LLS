package com.lsfg.android.benchmark

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BenchmarkLogWriter {

    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun format(
        ctx: Context,
        results: List<BenchmarkRunResult>,
        startedAtMs: Long,
        endedAtMs: Long,
        targetPackage: String?,
        renderWidth: Int,
        renderHeight: Int,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== DeepDrop Benchmark Report ===")
        sb.appendLine("Generated : ${sdf.format(Date(endedAtMs))}")
        sb.appendLine("Device    : ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
        sb.appendLine("Android   : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("Target    : ${targetPackage ?: "unknown"}")
        sb.appendLine("Render    : ${renderWidth}×${renderHeight}")
        sb.appendLine("Duration  : %.1f s".format((endedAtMs - startedAtMs) / 1000.0))
        sb.appendLine()
        sb.appendLine("%-4s  %-5s  %-8s  %-8s  %-8s  %-7s  %-7s  %-7s  %-6s".format(
            "×", "PREC", "REAL_FPS", "GEN_FPS", "POST_FPS", "P50_MS", "P99_MS", "JITTER", "STALLS"))
        sb.appendLine("-".repeat(74))
        results.forEach { r ->
            sb.appendLine(
                "%-4d  %-5s  %-8.2f  %-8.2f  %-8.2f  %-7.2f  %-7.2f  %-7.4f  %-6d".format(
                    r.multiplier,
                    if (r.framegenFp16) "FP16" else "FP32",
                    r.realFps, r.generatedFps, r.postedFps,
                    r.pacingMs?.p50Ms ?: 0.0,
                    r.pacingMs?.p99Ms ?: 0.0,
                    r.pacingMs?.jitterRatio ?: 0.0,
                    r.stallCount,
                )
            )
        }
        sb.appendLine()
        results.forEachIndexed { idx, r ->
            sb.appendLine("── Run ${idx + 1}: ×${r.multiplier} ${if (r.framegenFp16) "FP16" else "FP32"} ──")
            r.pacingMs?.let { p ->
                sb.appendLine("  Pacing: min=%.2f p50=%.2f p90=%.2f p99=%.2f max=%.2f ms  σ=%.3f".format(
                    p.minMs, p.p50Ms, p.p90Ms, p.p99Ms, p.maxMs, p.stddevMs))
            }
            r.profile?.let { p ->
                sb.appendLine("  Profile: copy=%.2f present=%.2f blit=%.2f total=%.2f ms (n=${p.samples})".format(
                    p.copyMs, p.presentMs, p.blitMs, p.totalMs))
            }
            r.vsyncAlignmentPercent?.let {
                sb.appendLine("  Vsync alignment: %.1f%%".format(it))
            }
        }
        return sb.toString()
    }

    fun write(ctx: Context, text: String): File {
        val dir = File(ctx.getExternalFilesDir(null), "benchmarks")
            .also { it.mkdirs() }
        val name = "bench_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
        val file = File(dir, name)
        file.writeText(text)
        return file
    }
}
