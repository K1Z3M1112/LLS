package com.firstt175.deepdrop.session

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/** Imports a trained notebook export (.zip or a single .param/.bin pair) into app-private storage. */
object CustomModelImporter {
    data class Result(val dir: File, val engine: Int, val label: String)

    fun import(ctx: Context, uri: Uri, displayName: String): Result {
        val out = File(ctx.filesDir, "my_models/${System.currentTimeMillis()}").apply { mkdirs() }
        if (displayName.endsWith(".zip", true) || displayName.endsWith(".model", true)) {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory) continue
                        val name = e.name.substringAfterLast('/')
                        if (name != "flownet.param" && name != "flownet.bin" && name != "ifrnet.param" && name != "ifrnet.bin") continue
                        File(out, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            } ?: error("Cannot open model file")
        } else {
            val name = displayName.substringAfterLast('/')
            if (name != "flownet.param" && name != "flownet.bin" && name != "ifrnet.param" && name != "ifrnet.bin") {
                error("Use an NCNN .zip or .param/.bin model export")
            }
            ctx.contentResolver.openInputStream(uri)?.use { it.copyTo(File(out, name).outputStream()) }
        }
        val rife = File(out, "flownet.param").length() > 0 && File(out, "flownet.bin").length() > 0
        val ifr = File(out, "ifrnet.param").length() > 0 && File(out, "ifrnet.bin").length() > 0
        if (!rife && !ifr) error("Model export must contain flownet.param/bin or ifrnet.param/bin")
        return Result(out, if (rife) 0 else 1, displayName.substringBeforeLast('.'))
    }
}
