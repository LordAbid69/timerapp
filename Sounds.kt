package com.example.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Synthesises a short wooden "tick" at runtime (no audio asset needed) and replays it from memory. */
class Ticker {
    private val track: AudioTrack?

    init {
        val sr = 22050
        val n = (sr * 0.045).toInt()
        val data = ShortArray(n) { i ->
            val t = i / sr.toDouble()
            val env = exp(-t * 140.0)
            val v = (sin(2 * PI * 1400 * t) * 0.7 + sin(2 * PI * 2800 * t) * 0.3) * env
            (v * 0.55 * Short.MAX_VALUE).toInt().toShort()
        }
        track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build().also { it.write(data, 0, n) }
        } catch (e: Exception) { null }
    }

    fun tick() {
        val t = track ?: return
        try { t.stop(); t.reloadStaticData(); t.play() } catch (_: Exception) { }
    }

    fun release() { try { track?.release() } catch (_: Exception) { } }
}

/** Looping alarm sound + vibration for when the timer ends. */
class AlarmPlayer(private val ctx: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
        stop()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            try { player?.release() } catch (_: Exception) { }
            player = null
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
    }

    fun stop() {
        player?.let {
            try { it.stop() } catch (_: Exception) { }
            try { it.release() } catch (_: Exception) { }
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }
}
