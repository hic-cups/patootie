package app.patootie.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Sfx { Tap, Pop, Sparkle, Success, Oops }

/**
 * Soft UI sounds, synthesized once into tiny WAVs in the cache dir and played
 * through SoundPool (low latency, sounds can overlap). No audio assets to ship.
 */
class CuteSounds(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<Sfx, Int>()
    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Call off the main thread once; synthesis takes a few milliseconds. */
    fun load() {
        val dir = File(context.cacheDir, "sfx-v1").apply { mkdirs() }
        for (sfx in Sfx.entries) {
            val file = File(dir, "${sfx.name}.wav")
            if (!file.exists()) file.writeBytes(wav(synth(sfx)))
            ids[sfx] = pool.load(file.path, 1)
        }
    }

    fun play(sfx: Sfx) {
        if (!enabled) return
        val id = ids[sfx] ?: return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun synth(sfx: Sfx): FloatArray = when (sfx) {
        // A gentle blip.
        Sfx.Tap -> tone(seconds = 0.07, from = 1046.0, to = 1318.0, decay = 40.0)
        // Bubble pop: quick upward sweep.
        Sfx.Pop -> tone(seconds = 0.11, from = 520.0, to = 1250.0, decay = 28.0)
        // Two high twinkles.
        Sfx.Sparkle -> mix(
            tone(0.25, 2093.0, 2093.0, 18.0, bell = true),
            tone(0.25, 2637.0, 2637.0, 18.0, bell = true), offset = 0.07,
        )
        // C–E–G–C music-box arpeggio.
        Sfx.Success -> listOf(1046.5, 1318.5, 1568.0, 2093.0).foldIndexed(FloatArray(0)) { i, acc, f ->
            mix(acc, tone(0.45, f, f, 7.0, bell = true), offset = i * 0.09)
        }
        // Soft sad "boo-oop" going down.
        Sfx.Oops -> mix(tone(0.16, 660.0, 520.0, 14.0), tone(0.22, 494.0, 392.0, 12.0), offset = 0.14)
    }

    private fun tone(seconds: Double, from: Double, to: Double, decay: Double, bell: Boolean = false): FloatArray {
        val n = (seconds * RATE).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i / RATE.toDouble()
            val freq = from + (to - from) * (i.toDouble() / n)
            phase += 2 * PI * freq / RATE
            val attack = (t / 0.004).coerceAtMost(1.0)
            val env = attack * exp(-decay * t)
            val wave = if (bell) sin(phase) + 0.3 * sin(phase * 2.01) + 0.12 * sin(phase * 3.98) else sin(phase)
            (wave * env * 0.6).toFloat()
        }
    }

    private fun mix(a: FloatArray, b: FloatArray, offset: Double): FloatArray {
        val start = (offset * RATE).toInt()
        val out = FloatArray(maxOf(a.size, start + b.size))
        a.copyInto(out)
        for (i in b.indices) out[start + i] += b[i]
        return out
    }

    private fun wav(samples: FloatArray): ByteArray {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) pcm.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        val data = pcm.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }
        return ByteArrayOutputStream().apply { write(header.array()); write(data) }.toByteArray()
    }

    private companion object {
        const val RATE = 44_100
        const val VOLUME = 0.35f
    }
}
