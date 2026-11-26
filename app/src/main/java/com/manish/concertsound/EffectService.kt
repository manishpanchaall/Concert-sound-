package com.manish.concertsound

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.audiofx.*
import android.os.Build
import android.os.IBinder
import android.widget.Toast

object Cfg {
    var eq = intArrayOf(0, 0, 0, 0, 0)
    var bass = 0
    var virt = 0
}

class EffectService : Service() {
    private class Fx(val eq: Equalizer, val bass: BassBoost, val virt: Virtualizer)

    private val fxs = HashMap<Int, Fx>()
    private var registered = false

    companion object {
        var inst: EffectService? = null
        fun refresh() { inst?.applyAll() }
    }

    private val rec = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val s = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (s < 0) return
            if (i.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                if (attach(s)) {
                    val pkg = i.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                    Toast.makeText(c, "Effect laga: $pkg", Toast.LENGTH_SHORT).show()
                }
            } else release(s)
        }
    }

    private fun attach(s: Int): Boolean {
        release(s)
        return try {
            val f = Fx(Equalizer(0, s), BassBoost(0, s), Virtualizer(0, s))
            fxs[s] = f
            apply(f)
            true
        } catch (e: Exception) { false }
    }

    private fun release(s: Int) {
        fxs.remove(s)?.let {
            try { it.eq.release(); it.bass.release(); it.virt.release() } catch (e: Exception) {}
        }
    }

    private fun apply(f: Fx) {
        try {
            f.eq.setEnabled(true)
            val r = f.eq.bandLevelRange
            val n = minOf(5, f.eq.numberOfBands.toInt())
            for (i in 0 until n) {
                val lvl = (Cfg.eq[i] * 100).coerceIn(r[0].toInt(), r[1].toInt())
                f.eq.setBandLevel(i.toShort(), lvl.toShort())
            }
            if (f.bass.strengthSupported) {
                f.bass.setStrength(Cfg.bass.toShort())
                f.bass.setEnabled(Cfg.bass > 0)
            }
            if (f.virt.strengthSupported) {
                f.virt.setStrength(Cfg.virt.toShort())
                f.virt.setEnabled(Cfg.virt > 0)
            }
        } catch (e: Exception) {}
    }

    fun applyAll() { fxs.values.forEach { apply(it) } }

    override fun onStartCommand(i: Intent?, fl: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("fx", "Concert Sound", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "fx")
            .setContentTitle("Concert Sound on hai")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
        inst = this
        if (!registered) {
            val f = IntentFilter()
            f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(rec, f, Context.RECEIVER_EXPORTED)
            else registerReceiver(rec, f)
            registered = true
            attach(0)
        }
        return START_STICKY
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        if (registered) { unregisterReceiver(rec); registered = false }
        fxs.keys.toList().forEach { release(it) }
        inst = null
        super.onDestroy()
    }
}
