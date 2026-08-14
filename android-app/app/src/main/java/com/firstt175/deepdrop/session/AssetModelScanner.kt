package com.firstt175.deepdrop.session

import android.content.Context
import java.io.File

/** Discovers every NCNN model directory shipped under assets/models/. */
data class AssetModelInfo(
    val dir: String,
    val label: String,
    val engine: Int,
    val paramName: String,
    val binName: String,
)

object AssetModelScanner {
    fun scan(ctx: Context): List<AssetModelInfo> {
        val roots = runCatching { ctx.assets.list("models")?.toList().orEmpty() }.getOrDefault(emptyList())
        return roots.mapNotNull { dir ->
            val files = runCatching { ctx.assets.list("models/$dir")?.toSet().orEmpty() }.getOrDefault(emptySet())
            when {
                "flownet.param" in files && "flownet.bin" in files ->
                    AssetModelInfo(dir, pretty(dir), 0, "flownet.param", "flownet.bin")
                "ifrnet.param" in files && "ifrnet.bin" in files ->
                    AssetModelInfo(dir, pretty(dir), 1, "ifrnet.param", "ifrnet.bin")
                else -> null
            }
        }.sortedBy { it.label.lowercase() }
    }

    fun extract(ctx: Context, model: AssetModelInfo): File {
        val safe = model.dir.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(ctx.filesDir, "ai_asset_models/$safe").apply { mkdirs() }
        val param = File(out, model.paramName)
        val bin = File(out, model.binName)
        if (!param.exists() || param.length() == 0L) copy(ctx, "models/${model.dir}/${model.paramName}", param)
        if (!bin.exists() || bin.length() == 0L) copy(ctx, "models/${model.dir}/${model.binName}", bin)
        return out
    }

    private fun copy(ctx: Context, asset: String, out: File) {
        ctx.assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
    }

    private fun pretty(s: String): String = s
        .replace('_', ' ')
        .replace(Regex("(?i)ensemblefalse"), "")
        .replace(Regex("(?i)ensembletrue"), " ensemble")
        .replace(Regex("(?i)fasttrue"), " fast")
        .replace(Regex("\\s+"), " ")
        .trim()
}
