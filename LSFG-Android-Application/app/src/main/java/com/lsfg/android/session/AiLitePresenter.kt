package com.lsfg.android.session

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

/**
 * Kotlin-side presenter for the [com.lsfg.android.prefs.FramegenEngine.AI_LITE] path.
 *
 * The native LSFG path (lsfg_render_loop.cpp) owns Vulkan/AHardwareBuffer output
 * presentation end-to-end and isn't touched by AI-lite at all — deliberately, since
 * rewiring that pipeline without on-device testing is too risky. Instead this class
 * is a small, independent software presenter: it receives real captured frames as
 * [Bitmap]s (see [CaptureEngine.setAiLiteFrameListener]), asks [TfliteFrameGenEngine]
 * for the intermediate frames, and paces posting all of them to the same overlay
 * [Surface] the mirror path would otherwise use, via [Surface.lockHardwareCanvas].
 *
 * Multiplier support: 2x uses one midpoint. 4x uses two nested midpoints, which is
 * an exact binary subdivision (t=1/4, 1/2, 3/4). 3x reuses the 4x subdivision and
 * drops the last frame — the timing slots stay evenly spaced but the two kept
 * frames sit at t≈1/4 and t≈1/2 rather than exact thirds, since this engine only
 * ever asks the network for literal midpoints. That's a real approximation, not a
 * bug: an arbitrary-t model would need a timestep input, which most tiny RIFE-lite
 * exports don't expose.
 */
class AiLitePresenter(
    private val engine: TfliteFrameGenEngine,
    private val multiplier: Int,
) {
    private var surface: Surface? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var previousBitmap: Bitmap? = null
    private var previousTimestampNanos: Long = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun attach(outputSurface: Surface) {
        detach()
        surface = outputSurface
        val t = HandlerThread("ai-lite-presenter").also { it.start() }
        thread = t
        handler = Handler(t.looper)
    }

    fun detach() {
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        surface = null
        previousBitmap?.recycle()
        previousBitmap = null
    }

    /** Called from the capture thread as each real frame arrives. Cheap: just posts to the presenter thread. */
    fun submitFrame(bitmap: Bitmap, timestampNanos: Long) {
        val h = handler ?: run { bitmap.recycle(); return }
        h.post { onFrameOnPresenterThread(bitmap, timestampNanos) }
    }

    private fun onFrameOnPresenterThread(current: Bitmap, timestampNanos: Long) {
        val prev = previousBitmap
        val prevTs = previousTimestampNanos
        if (prev == null) {
            presentNow(current, recycleAfter = false)
            previousBitmap = current
            previousTimestampNanos = timestampNanos
            return
        }

        val intervalNs = (timestampNanos - prevTs).coerceAtLeast(1L)
        val slotNs = intervalNs / multiplier.coerceAtLeast(1)

        val generated: List<Bitmap> = runCatching {
            when (multiplier) {
                4 -> {
                    val half = engine.interpolateMidpoint(prev, current) ?: return@runCatching emptyList()
                    val q1 = engine.interpolateMidpoint(prev, half)
                    val q3 = engine.interpolateMidpoint(half, current)
                    listOfNotNull(q1, half, q3)
                }
                3 -> {
                    val half = engine.interpolateMidpoint(prev, current) ?: return@runCatching emptyList()
                    val q1 = engine.interpolateMidpoint(prev, half)
                    listOfNotNull(q1, half)
                }
                else -> listOfNotNull(engine.interpolateMidpoint(prev, current))
            }
        }.getOrElse { e ->
            LsfgLog.w(TAG, "AI-lite interpolation failed, showing real frame only", e)
            emptyList()
        }

        // Schedule the generated frames evenly across this interval, then the real
        // frame last. If inference failed we still land on the real frame at the
        // full interval — same behaviour as bypass, just later than ideal.
        var delay = slotNs / 1_000_000L // ns -> ms for postDelayed
        for (g in generated) {
            handler?.postDelayed({ presentNow(g, recycleAfter = true) }, delay.coerceAtLeast(0))
            delay += slotNs / 1_000_000L
        }
        handler?.postDelayed({ presentNow(current, recycleAfter = false) }, delay.coerceAtLeast(0))

        // `prev` is superseded now that `current` is queued to become the new
        // previousBitmap; recycle it once its own presentation (scheduled on a
        // prior call) has had time to run by piggybacking on the same queue.
        handler?.post { prev.recycle() }
        previousBitmap = current
        previousTimestampNanos = timestampNanos
    }

    private fun presentNow(bitmap: Bitmap, recycleAfter: Boolean) {
        val s = surface ?: run { if (recycleAfter) bitmap.recycle(); return }
        if (!s.isValid) { if (recycleAfter) bitmap.recycle(); return }
        val canvas: Canvas = try {
            s.lockHardwareCanvas()
        } catch (t: Throwable) {
            runCatching { s.lockCanvas(null) }.getOrNull() ?: run {
                if (recycleAfter) bitmap.recycle()
                return
            }
        }
        try {
            val dst = Rect(0, 0, canvas.width, canvas.height)
            canvas.drawBitmap(bitmap, null, dst, paint)
        } finally {
            runCatching { s.unlockCanvasAndPost(canvas) }
        }
        if (recycleAfter) bitmap.recycle()
    }

    companion object {
        private const val TAG = "AiLitePresenter"
    }
}
