package com.manish.concertsound

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

private class Bq {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun peak(fs: Float, f: Float, db: Float, q: Float) {
        val a = 10f.pow(db / 40f)
        val w = 2f * PI.toFloat() * f / fs
        val al = sin(w) / (2f * q)
        val cw = cos(w)
        val a0 = 1f + al / a
        b0 = (1f + al * a) / a0
        b1 = -2f * cw / a0
        b2 = (1f - al * a) / a0
        a1 = -2f * cw / a0
        a2 = (1f - al / a) / a0
    }

    fun lowShelf(fs: Float, f: Float, db: Float) {
        val a = 10f.pow(db / 40f)
        val w = 2f * PI.toFloat() * f / fs
        val cw = cos(w)
        val al = sin(w) / 2f * sqrt(2f)
        val t = 2f * sqrt(a) * al
        val a0 = (a + 1f) + (a - 1f) * cw + t
        b0 = a * ((a + 1f) - (a - 1f) * cw + t) / a0
        b1 = 2f * a * ((a - 1f) - (a + 1f) * cw) / a0
        b2 = a * ((a + 1f) - (a - 1f) * cw - t) / a0
        a1 = -2f * ((a - 1f) + (a + 1f) * cw) / a0
        a2 = ((a + 1f) + (a - 1f) * cw - t) / a0
    }

    fun run(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

private class Comb(n: Int) {
    private val buf = FloatArray(max(n, 1))
    private var i = 0
    private var lp = 0f
    fun run(x: Float, g: Float, damp: Float): Float {
        val y = buf[i]
        lp = y * (1f - damp) + lp * damp
        buf[i] = x + lp * g
        i++
        if (i >= buf.size) i = 0
        return y
    }
}

private class Ap(n: Int) {
    private val buf = FloatArray(max(n, 1))
    private var i = 0
    fun run(x: Float): Float {
        val b = buf[i]
        val y = b - x
        buf[i] = x + b * 0.5f
        i++
        if (i >= buf.size) i = 0
        return y
    }
}

class ConcertProcessor : BaseAudioProcessor() {
    private var fs = 44100f
    private val eqL = Array(5) { Bq() }
    private val eqR = Array(5) { Bq() }
    private val shL = Bq()
    private val shR = Bq()
    private var lpL = 0f
    private var lpK = 0.02f
    private var combL = emptyArray<Comb>()
    private var combR = emptyArray<Comb>()
    private var apL1 = Ap(1)
    private var apL2 = Ap(1)
    private var apR1 = Ap(1)
    private var apR2 = Ap(1)
    private var pre = FloatArray(2)
    private var pi = 0
    private var preN = 1
    private var exc = 0f
    private var width = 1f
    private var wet = 0f
    private var fb = 0.75f
    private var makeup = 1f
    private var sig = Int.MIN_VALUE

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        fs = inputAudioFormat.sampleRate.toFloat()
        fun n(ms: Float) = (ms * fs / 1000f).toInt()
        combL = arrayOf(Comb(n(29.7f)), Comb(n(37.1f)), Comb(n(41.1f)), Comb(n(43.7f)))
        combR = arrayOf(Comb(n(30.5f)), Comb(n(38.0f)), Comb(n(42.2f)), Comb(n(45.1f)))
        apL1 = Ap(n(5.0f)); apL2 = Ap(n(1.7f))
        apR1 = Ap(n(5.3f)); apR2 = Ap(n(1.9f))
        pre = FloatArray(n(45f) + 2)
        pi = 0
        lpL = 0f
        lpK = 1f - exp(-2f * PI.toFloat() * 140f / fs)
        sig = Int.MIN_VALUE
        return inputAudioFormat
    }

    private fun update() {
        val b = Cfg.bass / 1000f
        val v = Cfg.virt / 1000f
        val f = floatArrayOf(60f, 230f, 910f, 3600f, 12000f)
        for (i in 0..4) {
            val g = (Cfg.eq[i] * 1.4f).coerceIn(-15f, 15f)
            eqL[i].peak(fs, f[i], g, 0.9f)
            eqR[i].peak(fs, f[i], g, 0.9f)
        }
        shL.lowShelf(fs, 95f, b * 9f)
        shR.lowShelf(fs, 95f, b * 9f)
        exc = b * 0.2f
        width = 1f + v * 1.4f
        wet = v * 0.55f
        fb = 0.72f + v * 0.16f
        preN = ((12f + v * 28f) * fs / 1000f).toInt().coerceIn(1, pre.size - 1)
        makeup = 1f + v * 0.12f + b * 0.12f
    }

    private fun lim(x: Float): Float {
        val a = abs(x)
        return if (a <= 0.8f) x else sign(x) * (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f))
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        var s = Cfg.bass * 31 + Cfg.virt
        for (e in Cfg.eq) s = s * 31 + e
        if (s != sig) {
            sig = s
            update()
        }
        val out = replaceOutputBuffer(n)
        while (inputBuffer.remaining() >= 4) {
            var l = inputBuffer.getShort() / 32768f
            var r = inputBuffer.getShort() / 32768f
            for (i in 0..4) {
                l = eqL[i].run(l)
                r = eqR[i].run(r)
            }
            l = shL.run(l)
            r = shR.run(r)
            val m0 = (l + r) * 0.5f
            lpL += lpK * (m0 - lpL)
            val h = tanh(lpL * 4f) * exc
            l += h
            r += h
            val m = (l + r) * 0.5f
            val sd = (l - r) * 0.5f * width
            l = m + sd
            r = m - sd
            if (wet > 0.001f) {
                pre[pi] = m
                val rd = pi - preN
                val x = pre[if (rd < 0) rd + pre.size else rd] * 0.1f
                pi++
                if (pi >= pre.size) pi = 0
                var wl = 0f
                var wr = 0f
                for (k in 0..3) {
                    wl += combL[k].run(x, fb, 0.35f)
                    wr += combR[k].run(x, fb, 0.35f)
                }
                wl = apL2.run(apL1.run(wl))
                wr = apR2.run(apR1.run(wr))
                l += wl * wet
                r += wr * wet
            }
            l = lim(l * makeup)
            r = lim(r * makeup)
            out.putShort((l * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            out.putShort((r * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }
}
