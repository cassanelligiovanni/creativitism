package com.creativitism.appredirector

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

/**
 * Smoked glass card: blur of the aurora behind + a dark warm overlay (not milky white).
 * White frost reads as steamy grey on real phone GPUs; charcoal tint keeps the
 * emulator's deeper look. Falls back to nearly-opaque charcoal if blur fails.
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val blurView: BlurView = BlurView(context)
    private val cornerRadius = 26f * resources.displayMetrics.density
    private val strokeWidth = 1f * resources.displayMetrics.density
    private val strokeRect = RectF()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = GLASS_TINT
    }
    private var blurSetupDone = false

    init {
        blurView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        blurView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        blurView.clipToOutline = true
        super.addView(blurView, -1, blurView.layoutParams)
        rimPaint.strokeWidth = strokeWidth
        setWillNotDraw(false)
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (child === blurView) {
            super.addView(child, index, params)
        } else {
            blurView.addView(child, index, params)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (blurSetupDone) return
        blurSetupDone = true
        try {
            val target = rootView.findViewById<BlurTarget>(R.id.blurTarget)
                ?: throw IllegalStateException("No BlurTarget with id blurTarget in hierarchy")
            val windowBackground = (context as? Activity)?.window?.decorView?.background
            blurView.setupWith(target)
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(22f)
                .setOverlayColor(BLUR_OVERLAY)
        } catch (t: Throwable) {
            tintPaint.color = FALLBACK_TINT
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rimPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            RIM_TOP, RIM_BOTTOM,
            Shader.TileMode.CLAMP,
        )
        val inset = strokeWidth / 2f
        strokeRect.set(inset, inset, w - inset, h - inset)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, tintPaint)
        canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, rimPaint)
    }

    private companion object {
        // Dark smoked charcoal overlays — keep phone glass deep like the emulator.
        val BLUR_OVERLAY: Int = 0xA80E0B09.toInt()
        val GLASS_TINT: Int = 0x33110C0A
        val FALLBACK_TINT: Int = 0xE60E0B09.toInt()
        const val RIM_TOP: Int = 0x2AFFFFFF
        const val RIM_BOTTOM: Int = 0x08FFFFFF
    }
}
