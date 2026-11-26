package com.manish.concertsound

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private val bars = ArrayList<SeekBar>()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun slider(root: LinearLayout, label: String, min: Int, max: Int, on: (Int) -> Unit) {
        val t = TextView(this)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.setPadding(0, dp(12), 0, 0)
        val s = SeekBar(this)
        s.max = max - min
        s.progress = -min
        s.tag = min
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                on(p + min)
                EffectService.refresh()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        root.addView(t)
        root.addView(s)
        bars.add(s)
    }

    private fun preset(eq: IntArray, bass: Int, virt: Int) {
        for (i in 0..4) bars[i].progress = eq[i] - (bars[i].tag as Int)
        bars[5].progress = bass
        bars[6].progress = virt
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#120D24"))
        root.setPadding(dp(20), dp(40), dp(20), dp(30))

        val title = TextView(this)
        title.text = "Concert Sound"
        title.textSize = 26f
        title.setTextColor(Color.parseColor("#FF4D8D"))
        root.addView(title)

        val info = TextView(this)
        info.text = "ON karo, phir Spotify ya YT Music chalao. Effect lagne par message aayega."
        info.setTextColor(Color.parseColor("#A69CCB"))
        info.setPadding(0, dp(6), 0, dp(12))
        root.addView(info)

        val sw = Switch(this)
        sw.text = "Concert Sound ON"
        sw.setTextColor(Color.WHITE)
        sw.setOnCheckedChangeListener { _, on ->
            val i = Intent(this, EffectService::class.java)
            if (on) startForegroundService(i) else stopService(i)
        }
        root.addView(sw)

        val names = listOf("Bass (60Hz)", "Low-mid", "Mid", "Upper-mid", "Treble")
        names.forEachIndexed { i, n -> slider(root, n, -12, 12) { Cfg.eq[i] = it } }
        slider(root, "Bass boost", 0, 1000) { Cfg.bass = it }
        slider(root, "Surround (virtualizer)", 0, 1000) { Cfg.virt = it }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        fun btn(n: String, eq: IntArray, bass: Int, virt: Int) {
            val bt = Button(this)
            bt.text = n
            bt.setOnClickListener { preset(eq, bass, virt) }
            row.addView(bt)
        }
        btn("Flat", intArrayOf(0, 0, 0, 0, 0), 0, 0)
        btn("Concert", intArrayOf(3, 1, -1, 2, 4), 500, 800)
        btn("Stadium", intArrayOf(5, 2, -2, 2, 5), 700, 1000)
        btn("Club", intArrayOf(7, 3, 0, 1, 2), 900, 500)
        btn("Vocal", intArrayOf(-3, 0, 4, 3, 1), 0, 300)
        val hs = HorizontalScrollView(this)
        hs.addView(row)
        root.addView(hs)

        val sc = ScrollView(this)
        sc.addView(root)
        setContentView(sc)
    }
}
