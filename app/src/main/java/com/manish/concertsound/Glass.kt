package com.manish.concertsound

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.LinearLayout

class GlassLayout(c: Context, private val src: View) : LinearLayout(c) {
    private var snap: Bitmap? = null
    private val sc = Canvas()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val loc = IntArray(2)
    private val sloc = IntArray(2)
    private var busy = false
    private val d = resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        p.isFilterBitmap = true
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h / 2f
        path.reset()
        path.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
        cv.save()
        cv.clipPath(path)
        cv.drawColor(Pal.BG)
        if (!busy) {
            busy = true
            try {
                val scale = 0.08f
                val bw = (w * scale).toInt().coerceAtLeast(2)
                val bh = (h * scale).toInt().coerceAtLeast(2)
                var b = snap
                if (b == null || b.width != bw || b.height != bh) {
                    b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    snap = b
                }
                b!!.eraseColor(Pal.BG)
                sc.setBitmap(b)
                getLocationInWindow(loc)
                src.getLocationInWindow(sloc)
                sc.save()
                sc.scale(scale, scale)
                sc.translate((sloc[0] - loc[0]).toFloat(), (sloc[1] - loc[1]).toFloat())
                src.draw(sc)
                sc.restore()
                val dx = w * 0.05f
                val dy = h * 0.14f
                cv.drawBitmap(b, null, RectF(-dx, -dy, w + dx, h + dy), p)
            } catch (e: Exception) {
            } finally {
                busy = false
            }
        }
        cv.drawColor(Color.argb(80, 12, 12, 22))
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(70, 255, 255, 255), Color.argb(14, 255, 255, 255), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        cv.restore()

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.3f * d
        p.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.argb(190, 255, 255, 255), Color.argb(28, 255, 255, 255), Color.argb(120, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        val i = 0.7f * d
        cv.drawRoundRect(RectF(i, i, w - i, h - i), r, r, p)
        p.shader = null
        p.style = Paint.Style.FILL
    }
}
