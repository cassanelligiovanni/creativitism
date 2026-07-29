package com.creativitism.appredirector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The warm ember aurora that sits behind everything: a deep charcoal base with four
 * huge, soft radial blobs that drift almost imperceptibly (full orbit in ~a minute).
 * The motion is what makes the glass cards' blur feel alive. Redraws at ~15 fps.
 */
class AuroraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private class Blob(
        val cx: Float,
        val cy: Float,
        val radiusDp: Float,
        val color: Int,
        val ampX: Float,
        val ampY: Float,
        val periodMs: Long,
        val phase: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shaderMatrix = Matrix()
    }

    private val density = resources.displayMetrics.density
    private val basePaint = Paint()

    private val blobs = listOf(
        Blob(0.12f, 0.02f, 440f, 0x33FF6A3D.toInt(), 0.045f, 0.030f, 46_000L, 0.0f),
        Blob(0.95f, 0.16f, 480f, 0x2AE5484D.toInt(), -0.055f, 0.035f, 52_000L, 1.3f),
        Blob(0.88f, 0.72f, 360f, 0x18FFB25E.toInt(), -0.035f, -0.045f, 60_000L, 2.1f),
        Blob(0.42f, 1.05f, 580f, 0x247A2E4D.toInt(), 0.050f, -0.020f, 50_000L, 3.7f),
    )

    private val frameMs = 64L
    private var animating = false
    private val frameCallback = object : Runnable {
        override fun run() {
            if (!animating) return
            invalidate()
            postDelayed(this, frameMs)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        basePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF14100D.toInt(), 0xFF0A0706.toInt(), 0xFF070504.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        blobs.forEach { blob ->
            val radius = blob.radiusDp * density
            val base = blob.color
            val mid = Color.argb(Color.alpha(base) / 2, Color.red(base), Color.green(base), Color.blue(base))
            blob.paint.shader = RadialGradient(
                0f, 0f, radius,
                intArrayOf(base, mid, Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)
        val t = SystemClock.uptimeMillis().toFloat()
        blobs.forEach { blob ->
            val angle = (t / blob.periodMs) * 2f * PI.toFloat() + blob.phase
            val cx = (blob.cx + blob.ampX * sin(angle)) * width
            val cy = (blob.cy + blob.ampY * cos(angle)) * height
            blob.shaderMatrix.setTranslate(cx, cy)
            blob.paint.shader.setLocalMatrix(blob.shaderMatrix)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blob.paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animating = true
        post(frameCallback)
    }

    override fun onDetachedFromWindow() {
        animating = false
        removeCallbacks(frameCallback)
        super.onDetachedFromWindow()
    }
}
