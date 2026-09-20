package com.firstt175.deepdrop.ui.screens

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal CapCut-style clip editor: preview the recording, drag the two
 * timeline handles to pick an in/out point, then export just that range as a
 * new clip back into the Recording Gallery. This is intentionally scoped to
 * trim only (no multi-clip timeline, transitions, text, etc.) — the rest of
 * a CapCut-style toolset can be layered onto this screen later.
 */
@Composable
fun VideoEditScreen(nav: NavHostController, recordingId: Long) {
    val ctx = LocalContext.current
    val uri = remember(recordingId) {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, recordingId)
    }

    var durationMs by remember { mutableStateOf(0L) }
    var trimStart by remember { mutableStateOf(0f) }
    var trimEnd by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportDoneName by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Duration is only known once the player finishes preparing; default the
    // trim range to the full clip as soon as it is.
    LaunchedEffect(player) {
        while (player.duration <= 0L) delay(50)
        durationMs = player.duration
        trimStart = 0f
        trimEnd = durationMs.toFloat()
    }

    // Keep playback looping inside the selected trim range instead of
    // running past it into footage that won't be exported.
    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = player.isPlaying
            if (player.isPlaying && trimEnd > trimStart && player.currentPosition >= trimEnd.toLong()) {
                player.seekTo(trimStart.toLong())
            }
            delay(100)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Edit clip",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = !exporting && durationMs > 0 && trimEnd > trimStart,
                onClick = {
                    player.pause()
                    exporting = true
                    exportProgress = 0f
                    exportError = null
                    exportClip(
                        ctx = ctx,
                        sourceUri = uri,
                        startMs = trimStart.toLong(),
                        endMs = trimEnd.toLong(),
                        onProgress = { exportProgress = it },
                        onDone = { name ->
                            exporting = false
                            exportDoneName = name
                        },
                        onError = { msg ->
                            exporting = false
                            exportError = msg
                        },
                    )
                },
            ) { Text("Export") }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = {
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        val pos = player.currentPosition
                        if (pos < trimStart.toLong() || pos >= trimEnd.toLong()) {
                            player.seekTo(trimStart.toLong())
                        }
                        player.play()
                    }
                }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "${formatMs(trimStart.toLong())} \u2013 ${formatMs(trimEnd.toLong())} " +
                    "(${formatMs((trimEnd - trimStart).toLong())}) / ${formatMs(durationMs)}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (durationMs > 0) {
                RangeSlider(
                    value = trimStart..trimEnd,
                    valueRange = 0f..durationMs.toFloat(),
                    onValueChange = { range ->
                        trimStart = range.start
                        trimEnd = range.endInclusive
                        player.seekTo(trimStart.toLong())
                    },
                )
            }
        }
    }

    if (exporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(exportProgress * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )
    }

    exportDoneName?.let { name ->
        AlertDialog(
            onDismissRequest = { exportDoneName = null; nav.popBackStack() },
            title = { Text("Saved") },
            text = { Text("Saved as $name in Movies/Deepdrop.") },
            confirmButton = {
                TextButton(onClick = { exportDoneName = null; nav.popBackStack() }) { Text("Done") }
            },
        )
    }

    exportError?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text("Export failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { exportError = null }) { Text("OK") } },
        )
    }
}

/**
 * Re-encodes just [startMs, endMs) of [sourceUri] via Media3 Transformer,
 * then copies the result into MediaStore next to the original recordings so
 * it shows up back in the gallery.
 */
@OptIn(UnstableApi::class)
private fun exportClip(
    ctx: Context,
    sourceUri: Uri,
    startMs: Long,
    endMs: Long,
    onProgress: (Float) -> Unit,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val tempFile = File(ctx.cacheDir, "edit_${System.currentTimeMillis()}.mp4")

    val clippedItem = MediaItem.Builder()
        .setUri(sourceUri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs.coerceAtLeast(startMs + 1))
                .build(),
        )
        .build()

    lateinit var transformer: Transformer
    transformer = Transformer.Builder(ctx)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                saveExportedFile(ctx, tempFile, onDone, onError)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                tempFile.delete()
                onError(exportException.message ?: "Unknown error")
            }
        })
        .build()

    transformer.start(clippedItem, tempFile.absolutePath)

    // Standard Media3 progress-poll pattern: keep asking until the export is
    // no longer running.
    val handler = Handler(Looper.getMainLooper())
    val progressHolder = ProgressHolder()
    handler.post(object : Runnable {
        override fun run() {
            val state = transformer.getProgress(progressHolder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress(progressHolder.progress / 100f)
            }
            if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                handler.postDelayed(this, 300)
            }
        }
    })
}

private fun saveExportedFile(
    ctx: Context,
    tempFile: File,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val name = "Deepdrop_edit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Deepdrop")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val outUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (outUri == null) {
            tempFile.delete()
            onError("Could not create output file")
            return
        }
        resolver.openOutputStream(outUri)?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        }
        resolver.update(
            outUri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        )
        tempFile.delete()
        onDone(name)
    } catch (e: Exception) {
        tempFile.delete()
        onError(e.message ?: "Failed to save clip")
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
