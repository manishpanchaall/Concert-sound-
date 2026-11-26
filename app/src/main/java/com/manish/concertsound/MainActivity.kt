package com.manish.concertsound

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.*
import kotlin.math.roundToInt
import kotlin.math.sin

object Pal {
    val BG = Color.parseColor("#0B0B10")
    val SURF = Color.parseColor("#14141C")
    val LINE = Color.parseColor("#262633")
    val TEXT = Color.parseColor("#F2F2F6")
    val MUTE = Color.parseColor("#8B8B9C")
    val A1 = Color.parseColor("#A99BFF")
    val A2 = Color.parseColor("#FFC2A1")
    val O_OFF1 = Color.parseColor("#4A4A58")
    val O_OFF2 = Color.parseColor("#2A2A36")
    val O_OFF3 = Color.parseColor("#15151D")
    val O_ON1 = Color.parseColor("#F4EFFF")
    val O_ON2 = Color.parseColor("#9D8CFF")
    val O_ON3 = Color.parseColor("#2B2266")
}

fun lerpC(a: Int, b: Int, f: Float): Int {
    fun m(x: Int, y: Int) = (x + (y - x) * f).toInt()
    return Color.argb(
        m(Color.alpha(a), Color.alpha(b)),
        m(Color.red(a), Color.red(b)),
        m(Color.green(a), Color.green(b)),
        m(Color.blue(a), Color.blue(b))
    )
}

class OrbView(c: Context) : View(c) {
    var power = 0f
    private var t = 0f
    private var rp = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private val a1 = ValueAnimator.ofFloat(0f, 6.2832f).apply {
        duration = 5000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    private val a2 = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rp = it.animatedValue as Float }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); a1.start(); a2.start() }
    override fun onDetachedFromWindow() { a1.cancel(); a2.cancel(); super.onDetachedFromWindow() }

    fun setOn(on: Boolean) {
        val a = ValueAnimator.ofFloat(power, if (on) 1f else 0f)
        a.duration = 800
        a.interpolator = DecelerateInterpolator(1.6f)
        a.addUpdateListener { power = it.animatedValue as Float }
        a.start()
    }

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.26f
        val boost = (Cfg.bass + Cfg.virt) / 2000f
        val br = 1f + 0.035f * sin(t) * (0.3f + 0.7f * power)

        // glow
        val ga = (20 + 70 * power + 50 * boost * power).toInt().coerceIn(0, 255)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 2.6f, Color.argb(ga, 169, 155, 255),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 2.6f, p)

        // ripples
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        for (k in 0..2) {
            val pr = (rp + k / 3f) % 1f
            val rad = r * (1.15f + pr * (0.5f + 0.6f * power))
            val al = ((1f - pr) * 70f * power).toInt().coerceIn(0, 255)
            p.color = Color.argb(al, 169, 155, 255)
            cv.drawCircle(cx, cy, rad, p)
        }

        // orbiting light
        p.strokeWidth = 2f * d
        p.shader = SweepGradient(cx, cy,
            intArrayOf(Color.TRANSPARENT, Color.argb((210 * power).toInt().coerceIn(0, 255), 255, 194, 161)),
            floatArrayOf(0f, 1f))
        cv.save()
        cv.rotate(rp * 360f, cx, cy)
        cv.drawCircle(cx, cy, r * 1.3f, p)
        cv.restore()
        p.shader = null

        // body
        val c1 = lerpC(Pal.O_OFF1, Pal.O_ON1, power)
        val c2 = lerpC(Pal.O_OFF2, Pal.O_ON2, power)
        val c3 = lerpC(Pal.O_OFF3, Pal.O_ON3, power)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.5f * br,
            intArrayOf(c1, c2, c3), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * br, p)
        p.shader = null

        // rim
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * d
        p.color = Color.argb(40, 255, 255, 255)
        cv.drawCircle(cx, cy, r * br, p)
    }
}

class Slim(c: Context, val lo: Int, val hi: Int, val onChange: (Int) -> Unit) : View(c) {
    var value = 0
    private var press = 0f
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (38 * d).toInt())
    }

    private fun frac() = (value - lo).toFloat() / (hi - lo)

    override fun onDraw(cv: Canvas) {
        val pad = 14 * d
        val w = width - 2 * pad
        val cy = height / 2f
        val x = pad + w * frac()
        val ox = pad + w * ((0 - lo).toFloat() / (hi - lo))

        p.style = Paint.Style.FILL
        p.shader = null
        p.color = Pal.LINE
        cv.drawRoundRect(RectF(pad, cy - 2 * d, pad + w, cy + 2 * d), 2 * d, 2 * d, p)

        p.shader = LinearGradient(pad, 0f, pad + w, 0f, Pal.A2, Pal.A1, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(minOf(ox, x), cy - 2 * d, maxOf(ox, x), cy + 2 * d), 2 * d, 2 * d, p)
        p.shader = null

        p.color = Color.argb((70 * press).toInt(), 169, 155, 255)
        cv.drawCircle(x, cy, (9 + 9 * press) * d, p)
        p.color = Pal.TEXT
        cv.drawCircle(x, cy, (7 + press) * d, p)
    }

    private fun setFromX(x: Float) {
        val pad = 14 * d
        val f = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        val v = (lo + f * (hi - lo)).roundToInt()
        if (v != value) {
            value = v
            onChange(v)
            invalidate()
        }
    }

    private fun pressTo(target: Float) {
        val a = ValueAnimator.ofFloat(press, target)
        a.duration = 180
        a.addUpdateListener { press = it.animatedValue as Float; invalidate() }
        a.start()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                pressTo(1f)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                setFromX(e.x)
            }
            MotionEvent.ACTION_MOVE -> setFromX(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressTo(0f)
        }
        return true
    }

    fun glideTo(v: Int) {
        anim?.cancel()
        val a = ValueAnimator.ofInt(value, v)
        a.duration = 520
        a.interpolator = DecelerateInterpolator(2f)
        a.addUpdateListener {
            value = it.animatedValue as Int
            onChange(value)
            invalidate()
        }
        anim = a
        a.start()
    }
}

class MainActivity : Activity() {
    private val sliders = ArrayList<Slim>()
    private val chips = ArrayList<Chip>()
    private var selected = -1
    private var on = false

    private class Chip(val tv: TextView, val bg: GradientDrawable)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun lp(top: Int): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(-1, -2)
        l.topMargin = dp(top)
        return l
    }

    private fun tv(s: String, size: Float, color: Int, face: String = "sans-serif"): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        t.typeface = Typeface.create(face, Typeface.NORMAL)
        return t
    }

    private fun label(s: String): TextView {
        val t = tv(s.uppercase(), 11f, Pal.MUTE, "sans-serif-medium")
        t.letterSpacing = 0.16f
        return t
    }

    private fun card(root: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = shape(Pal.SURF, 24, Pal.LINE)
        c.setPadding(dp(20), dp(18), dp(20), dp(14))
        c.addView(label(title))
        root.addView(c, lp(16))
        return c
    }

    private fun addSlider(c: LinearLayout, name: String, lo: Int, hi: Int,
                          fmt: (Int) -> String, cb: (Int) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(14), 0, 0)
        val value = tv(fmt(0), 13f, Pal.MUTE)
        row.addView(tv(name, 15f, Pal.TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(value)
        val s = Slim(this, lo, hi) { v ->
            value.text = fmt(v)
            cb(v)
            EffectService.refresh()
        }
        c.addView(row)
        c.addView(s, LinearLayout.LayoutParams(-1, -2))
        sliders.add(s)
    }

    private fun paintChip(ch: Chip, active: Boolean) {
        val bgFrom = if (active) Pal.SURF else Pal.TEXT
        val bgTo = if (active) Pal.TEXT else Pal.SURF
        val txFrom = if (active) Pal.MUTE else Pal.BG
        val txTo = if (active) Pal.BG else Pal.MUTE
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 280
        a.addUpdateListener {
            val f = it.animatedValue as Float
            ch.bg.setColor(lerpC(bgFrom, bgTo, f))
            ch.tv.setTextColor(lerpC(txFrom, txTo, f))
        }
        a.start()
    }

    private fun fade(v: TextView, s: String) {
        v.animate().alpha(0f).setDuration(140).withEndAction {
            v.text = s
            v.animate().alpha(1f).setDuration(260).start()
        }.start()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Pal.BG)
        root.setPadding(dp(22), dp(52), dp(22), dp(56))

        root.addView(label("Concert Sound"))
        root.addView(tv("Feel the room,\nnot just the song.", 30f, Pal.TEXT, "sans-serif-light"), lp(10))

        val orb = OrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(-1, dp(300)))
        val status = tv("Tap the orb to begin", 14f, Pal.MUTE)
        status.gravity = Gravity_CENTER
        root.addView(status, lp(0))

        orb.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260).start()
            }
            false
        }
        orb.setOnClickListener {
            on = !on
            orb.setOn(on)
            fade(status, if (on) "Active. Now open your music app." else "Tap the orb to begin")
            val i = Intent(this, EffectService::class.java)
            if (on) startForegroundService(i) else stopService(i)
        }

        // presets
        root.addView(label("Presets"), lp(28))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 0, dp(10), 0)
        data class Pre(val n: String, val eq: IntArray, val bass: Int, val virt: Int)
        val list = listOf(
            Pre("Flat", intArrayOf(0, 0, 0, 0, 0), 0, 0),
            Pre("Concert Hall", intArrayOf(3, 1, -1, 2, 4), 500, 800),
            Pre("Stadium", intArrayOf(5, 2, -2, 2, 5), 700, 1000),
            Pre("Club", intArrayOf(7, 3, 0, 1, 2), 900, 500),
            Pre("Vocal", intArrayOf(-3, 0, 4, 3, 1), 0, 300),
            Pre("Gym", intArrayOf(6, 2, 0, 1, 3), 800, 400)
        )
        list.forEachIndexed { idx, p ->
            val t = tv(p.n, 14f, Pal.MUTE, "sans-serif-medium")
            val bg = shape(Pal.SURF, 22, Pal.LINE)
            t.background = bg
            t.setPadding(dp(20), dp(11), dp(20), dp(11))
            val ch = Chip(t, bg)
            chips.add(ch)
            t.setOnClickListener {
                if (selected == idx) return@setOnClickListener
                if (selected >= 0) paintChip(chips[selected], false)
                selected = idx
                paintChip(ch, true)
                t.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                for (k in 0..4) sliders[k].glideTo(p.eq[k])
                sliders[5].glideTo(p.bass)
                sliders[6].glideTo(p.virt)
            }
            val l = LinearLayout.LayoutParams(-2, -2)
            l.rightMargin = dp(10)
            row.addView(t, l)
        }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.overScrollMode = View.OVER_SCROLL_NEVER
        hs.addView(row)
        root.addView(hs, lp(12))

        // equalizer
        val eqCard = card(root, "Equalizer")
        val names = listOf("Bass", "Warmth", "Body", "Presence", "Air")
        names.forEachIndexed { i, n ->
            addSlider(eqCard, n, -12, 12, { v -> (if (v > 0) "+" else "") + v + " dB" }) { Cfg.eq[i] = it }
        }

        // space
        val fxCard = card(root, "Space")
        addSlider(fxCard, "Bass boost", 0, 1000, { v -> (v / 10).toString() + "%" }) { Cfg.bass = it }
        addSlider(fxCard, "Surround", 0, 1000, { v -> (v / 10).toString() + "%" }) { Cfg.virt = it }

        val foot = tv("Real noise cancellation comes from your earbuds, not from this app. " +
            "Effects apply to apps that expose their audio session.", 12f, Pal.MUTE)
        root.addView(foot, lp(22))

        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(36).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(i * 70L).setDuration(700)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }

        val sc = ScrollView(this)
        sc.setBackgroundColor(Pal.BG)
        sc.isVerticalScrollBarEnabled = false
        sc.overScrollMode = View.OVER_SCROLL_NEVER
        sc.isFillViewport = true
        sc.addView(root)
        setContentView(sc)
    }

    companion object {
        const val Gravity_CENTER = android.view.Gravity.CENTER
    }
}
