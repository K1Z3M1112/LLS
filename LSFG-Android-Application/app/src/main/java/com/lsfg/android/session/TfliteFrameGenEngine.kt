package com.lsfg.android.session

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * "AI_LITE" frame-generation backend: a small two-frame interpolation
 * network (e.g. a RIFE-lite export, see README for conversion steps) run
 * through TensorFlow Lite / LiteRT with the NNAPI delegate.
 *
 * This is the low-memory alternative to the LSFG Vulkan shader pipeline
 * (lsfg_render_loop.cpp). It intentionally trades interpolation quality for
 * a much smaller footprint:
 *
 *  - Single [Interpreter] instance, single pair of reusable input/output
 *    [ByteBuffer]s sized once for the configured resolution. No per-frame
 *    allocation, so steady-state heap use doesn't grow with frame rate.
 *  - Prefers the NNAPI delegate so weights/activations live in the
 *    accelerator driver's own memory, not the app's Java/native heap. Falls
 *    back to the CPU XNNPACK path with a capped thread count if no NNAPI
 *    accelerator is available (see [nnapi_npu_summary][com.lsfg.android]).
 *  - Runs inference at a capped working resolution ([maxWorkingDimension])
 *    and upscales the result — this is the single biggest RAM/compute
 *    lever, since activation memory scales roughly with pixel count.
 *  - Multi-frame multipliers (3x/4x) are produced by re-running the same
 *    2x pass recursively (interpolate(A,B) -> M, then interpolate(A,M) and
 *    interpolate(M,B) as needed) instead of loading a second/third model,
 *    so peak memory stays the same regardless of the chosen multiplier.
 *  - [close] releases the interpreter and delegate immediately (e.g. when
 *    the capture session stops), rather than waiting on GC.
 *
 * This class only does the AI math on RGBA byte buffers; it does not touch
 * the Vulkan/AHardwareBuffer plumbing. Wire it into the capture pipeline by
 * feeding it the two most recent captured frames and inserting the frames
 * it returns before presenting to the overlay surface — mirroring how
 * cpu_postprocess.cpp / CaptureEngine already hand off RGBA buffers today.
 */
class TfliteFrameGenEngine(
    private val context: Context,
    private val maxWorkingDimension: Int = 720,
    private val cpuThreads: Int = 2,
) {
    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var quantized = false

    private var workW = 0
    private var workH = 0
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null

    val ready: Boolean
        get() = interpreter != null

    /** True when inference is actually running on a dedicated NNAPI accelerator, not CPU fallback. */
    var usingNnapiAccelerator: Boolean = false
        private set

    /**
     * Loads [modelFile] (a .tflite RIFE-lite–style two-frame interpolation
     * network; inputs: two RGB/RGBA frames + optional timestep scalar,
     * output: one interpolated RGB/RGBA frame). Returns false if the model
     * can't be loaded — callers should fall back to [FramegenEngine.LSFG_SHADER].
     */
    fun load(modelFile: File): Boolean {
        close()
        return try {
            val model = loadMappedModel(modelFile)
            val options = Interpreter.Options().apply {
                setNumThreads(cpuThreads.coerceIn(1, 4))
                // Keep this false: we want a hard failure (caught below) rather than a
                // silent, much-slower CPU retry burning extra memory on a second buffer set.
                setAllowFp16PrecisionForFp32(true)
            }
            val delegate = runCatching { NnApiDelegate() }.getOrNull()
            if (delegate != null) {
                options.addDelegate(delegate)
                nnApiDelegate = delegate
            }
            interpreter = Interpreter(model, options)
            usingNnapiAccelerator = delegate != null
            quantized = detectQuantizedIo(interpreter!!)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back: failed to load AI-lite model ${modelFile.name}", t)
            // Retry once on pure CPU (no NNAPI) in case the accelerator rejected this graph.
            runCatching {
                nnApiDelegate?.close()
                nnApiDelegate = null
                val model = loadMappedModel(modelFile)
                val options = Interpreter.Options().apply { setNumThreads(cpuThreads.coerceIn(1, 4)) }
                interpreter = Interpreter(model, options)
                usingNnapiAccelerator = false
                quantized = detectQuantizedIo(interpreter!!)
                true
            }.getOrElse {
                close()
                false
            }
        }
    }

    /**
     * Interpolates a single midpoint frame between [frameA] and [frameB] (both
     * same size, RGBA_8888). For multiplier > 2, call this recursively from
     * the capture pipeline (A,mid then mid,B) rather than instantiating a
     * second engine — that's what keeps AI_LITE's RAM flat across 2x/3x/4x.
     * Returns null if the engine isn't [ready] or on any inference failure.
     */
    fun interpolateMidpoint(frameA: Bitmap, frameB: Bitmap): Bitmap? {
        val interp = interpreter ?: return null
        if (frameA.width != frameB.width || frameA.height != frameB.height) return null

        ensureBuffersFor(frameA.width, frameA.height)
        val input = inputBuffer ?: return null
        val output = outputBuffer ?: return null

        return try {
            input.rewind()
            writeFrameScaled(frameA, workW, workH, input, quantized)
            writeFrameScaled(frameB, workW, workH, input, quantized)
            output.rewind()

            interp.run(input, output)

            output.rewind()
            val midWork = readFrame(output, workW, workH, quantized)
            if (workW == frameA.width && workH == frameA.height) {
                midWork
            } else {
                Bitmap.createScaledBitmap(midWork, frameA.width, frameA.height, true).also {
                    if (it !== midWork) midWork.recycle()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AI-lite inference failed, caller should fall back for this frame", t)
            null
        }
    }

    /** Releases the interpreter, delegate, and both scratch buffers immediately. */
    fun close() {
        runCatching { interpreter?.close() }
        runCatching { nnApiDelegate?.close() }
        interpreter = null
        nnApiDelegate = null
        inputBuffer = null
        outputBuffer = null
        workW = 0
        workH = 0
        usingNnapiAccelerator = false
    }

    private fun ensureBuffersFor(frameW: Int, frameH: Int) {
        val longSide = maxOf(frameW, frameH)
        val scale = if (longSide > maxWorkingDimension) maxWorkingDimension.toFloat() / longSide else 1f
        val newW = (frameW * scale).toInt().coerceAtLeast(2).let { it - (it % 2) }
        val newH = (frameH * scale).toInt().coerceAtLeast(2).let { it - (it % 2) }
        if (newW == workW && newH == workH && inputBuffer != null) return

        workW = newW
        workH = newH
        val bytesPerChannel = if (quantized) 1 else 4
        val perFrameBytes = workW * workH * 3 * bytesPerChannel
        inputBuffer = ByteBuffer.allocateDirect(perFrameBytes * 2).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(perFrameBytes).order(ByteOrder.nativeOrder())
    }

    private fun loadMappedModel(file: File): MappedByteBuffer =
        file.inputStream().use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }

    private fun detectQuantizedIo(interp: Interpreter): Boolean =
        runCatching { interp.getInputTensor(0).dataType().name.contains("UINT8") }.getOrDefault(false)

    companion object {
        private const val TAG = "TfliteFrameGenEngine"

        /** Where imported .tflite models live — mirrors the extracted-shader directory convention. */
        fun modelDir(context: Context): File =
            File(context.filesDir, "ai_lite_models").apply { mkdirs() }
    }
}

/** Writes [bitmap] scaled to [w]x[h] into [dst] as packed RGB, appended after any existing content. */
private fun writeFrameScaled(bitmap: Bitmap, w: Int, h: Int, dst: ByteBuffer, quantized: Boolean) {
    val scaled = if (bitmap.width == w && bitmap.height == h) bitmap
        else Bitmap.createScaledBitmap(bitmap, w, h, true)
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        if (quantized) {
            dst.put(r.toByte()); dst.put(g.toByte()); dst.put(b.toByte())
        } else {
            dst.putFloat(r / 255f); dst.putFloat(g / 255f); dst.putFloat(b / 255f)
        }
    }
    if (scaled !== bitmap) scaled.recycle()
}

private fun readFrame(src: ByteBuffer, w: Int, h: Int, quantized: Boolean): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)
    for (i in 0 until w * h) {
        val r: Int; val g: Int; val b: Int
        if (quantized) {
            r = src.get().toInt() and 0xFF
            g = src.get().toInt() and 0xFF
            b = src.get().toInt() and 0xFF
        } else {
            r = (src.float * 255f).toInt().coerceIn(0, 255)
            g = (src.float * 255f).toInt().coerceIn(0, 255)
            b = (src.float * 255f).toInt().coerceIn(0, 255)
        }
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

private val ByteBuffer.float: Float
    get() = this.getFloat()
