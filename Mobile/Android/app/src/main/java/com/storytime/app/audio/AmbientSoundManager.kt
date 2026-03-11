package com.storytime.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private data class Crackle(var remaining: Int, val amplitude: Float, val decay: Float, var elapsed: Int)

enum class AmbientSound(val key: String, val displayName: String) {
    WHITE_NOISE("whiteNoise", "White Noise"),
    BROWN_NOISE("brownNoise", "Brown Noise"),
    PINK_NOISE("pinkNoise", "Pink Noise"),
    SOFT_RAIN("softRain", "Soft Rain"),
    OCEAN_WAVES("oceanWaves", "Ocean Waves"),
    CRACKLING_FIREPLACE("cracklingFireplace", "Fireplace"),
    FOREST_NIGHT("forestNight", "Forest Night"),
    HEARTBEAT("heartbeat", "Heartbeat");

    companion object {
        fun fromKey(key: String): AmbientSound =
            entries.firstOrNull { it.key == key } ?: WHITE_NOISE
    }
}

class AmbientSoundManager {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSound = MutableStateFlow(AmbientSound.WHITE_NOISE)
    val currentSound: StateFlow<AmbientSound> = _currentSound.asStateFlow()

    private val _volume = MutableStateFlow(0.5f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var fadeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_FRAMES = 2048
    }

    fun play(sound: AmbientSound, fadeDurationMs: Long = 3000L) {
        stop(fadeDurationMs = 0)
        _currentSound.value = sound

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(BUFFER_SIZE_FRAMES * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.setVolume(0f)
        track.play()
        audioTrack = track
        _isPlaying.value = true

        val generator = createGenerator(sound)

        playbackJob = scope.launch {
            val buffer = FloatArray(BUFFER_SIZE_FRAMES)
            while (isActive) {
                for (i in buffer.indices) {
                    buffer[i] = generator()
                }
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }

        fadeVolume(targetVolume = _volume.value, durationMs = fadeDurationMs)
    }

    fun stop(fadeDurationMs: Long = 1000L) {
        if (!_isPlaying.value) return
        fadeVolume(targetVolume = 0f, durationMs = fadeDurationMs) {
            playbackJob?.cancel()
            playbackJob = null
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
            _isPlaying.value = false
        }
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        audioTrack?.setVolume(vol)
    }

    fun release() {
        fadeJob?.cancel()
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        scope.cancel()
    }

    // MARK: - Noise Generators

    private fun createGenerator(sound: AmbientSound): () -> Float {
        return when (sound) {
            AmbientSound.WHITE_NOISE -> {
                { (Random.nextFloat() * 2f - 1f) * 0.3f }
            }

            AmbientSound.BROWN_NOISE -> {
                var lastSample = 0f
                val generator: () -> Float = {
                    val white = Random.nextFloat() * 2f - 1f
                    lastSample = (lastSample + 0.02f * white) / 1.02f
                    lastSample * 3.5f * 0.3f
                }
                generator
            }

            AmbientSound.PINK_NOISE -> {
                val pinkB = FloatArray(7)
                val generator: () -> Float = {
                    val white = Random.nextFloat() * 2f - 1f
                    // Voss-McCartney pink noise approximation
                    pinkB[0] = 0.99886f * pinkB[0] + white * 0.0555179f
                    pinkB[1] = 0.99332f * pinkB[1] + white * 0.0750759f
                    pinkB[2] = 0.96900f * pinkB[2] + white * 0.1538520f
                    pinkB[3] = 0.86650f * pinkB[3] + white * 0.3104856f
                    pinkB[4] = 0.55000f * pinkB[4] + white * 0.5329522f
                    pinkB[5] = -0.7616f * pinkB[5] - white * 0.0168980f
                    val pink = pinkB[0] + pinkB[1] + pinkB[2] + pinkB[3] + pinkB[4] + pinkB[5] + pinkB[6] + white * 0.5362f
                    pinkB[6] = white * 0.115926f
                    pink * 0.11f * 0.3f
                }
                generator
            }

            AmbientSound.SOFT_RAIN -> {
                var filterState = 0f
                val cutoff = 800f / SAMPLE_RATE.toFloat()
                val rc = 1f / (cutoff * 2f * PI.toFloat())
                val dt = 1f / SAMPLE_RATE.toFloat()
                val alpha = dt / (rc + dt)
                val generator: () -> Float = {
                    val white = Random.nextFloat() * 2f - 1f
                    // Low-pass filter for rain-like sound
                    filterState += alpha * (white - filterState)
                    // Occasional louder drops
                    val drop = if (Random.nextFloat() < 0.001f) Random.nextFloat() * 0.3f + 0.3f else 0f
                    (filterState * 1.5f + drop) * 0.3f
                }
                generator
            }

            AmbientSound.OCEAN_WAVES -> {
                var phase = 0.0
                var filterState = 0f
                val dtSec = 1.0 / SAMPLE_RATE.toDouble()
                val cutoff = 400f / SAMPLE_RATE.toFloat()
                val rc = 1f / (cutoff * 2f * PI.toFloat())
                val dtf = 1f / SAMPLE_RATE.toFloat()
                val alphaF = dtf / (rc + dtf)
                val generator: () -> Float = {
                    phase += dtSec
                    // Wave swell with varying period (8-12s cycle)
                    val cyclePeriod = 10.0 + 2.0 * sin(phase / 60.0)
                    val swell = (0.5 * (1.0 - cos(2.0 * PI * phase / cyclePeriod))).toFloat()
                    // Low-pass filtered noise for wave wash
                    val white = Random.nextFloat() * 2f - 1f
                    filterState += alphaF * (white - filterState)
                    // Foam/hiss at wave peaks
                    val foam = white * 0.15f * swell * swell
                    (filterState * swell * 0.8f + foam) * 0.3f
                }
                generator
            }

            AmbientSound.CRACKLING_FIREPLACE -> {
                var brownLast = 0f
                var filterState = 0f
                val cutoff = 200f / SAMPLE_RATE.toFloat()
                val rc = 1f / (cutoff * 2f * PI.toFloat())
                val dtf = 1f / SAMPLE_RATE.toFloat()
                val alphaF = dtf / (rc + dtf)
                val crackles = mutableListOf<Crackle>()
                val generator: () -> Float = {
                    // Warm base: brown noise filtered at ~200 Hz
                    val white = Random.nextFloat() * 2f - 1f
                    brownLast = (brownLast + 0.02f * white) / 1.02f
                    filterState += alphaF * (brownLast * 3.5f - filterState)
                    val warmBase = filterState

                    // Spawn new crackle impulses (~13/sec)
                    if (Random.nextFloat() < 0.0003f && crackles.size < 4) {
                        val amp = Random.nextFloat() * 0.6f + 0.4f
                        val decayVal = Random.nextFloat() * 300f + 100f
                        crackles.add(Crackle(remaining = (decayVal * 3).toInt(), amplitude = amp, decay = decayVal, elapsed = 0))
                    }

                    // Sum active crackles
                    var crackleSum = 0f
                    val iter = crackles.iterator()
                    while (iter.hasNext()) {
                        val c = iter.next()
                        val env = c.amplitude * exp(-c.elapsed.toFloat() / c.decay)
                        crackleSum += env * (Random.nextFloat() * 2f - 1f)
                        c.elapsed++
                        c.remaining--
                        if (c.remaining <= 0) iter.remove()
                    }

                    (warmBase * 0.6f + crackleSum * 0.4f) * 0.3f
                }
                generator
            }

            AmbientSound.FOREST_NIGHT -> {
                var chirpPhase = 0.0
                var windPhase = 0.0
                val forestPinkB = FloatArray(7)
                val dtSec = 1.0 / SAMPLE_RATE.toDouble()
                val generator: () -> Float = {
                    chirpPhase += dtSec
                    windPhase += dtSec

                    // Cricket 1: 4500 Hz chirps every ~0.5s
                    val chirpCycle1 = chirpPhase % 0.5
                    val cricket1 = if (chirpCycle1 < 0.15) {
                        val env = (1.0 - chirpCycle1 / 0.15).toFloat()
                        (sin(2.0 * PI * 4500.0 * chirpPhase)).toFloat() * env * 0.3f
                    } else 0f

                    // Cricket 2: 5200 Hz, offset by 0.25s
                    val chirpCycle2 = (chirpPhase + 0.25) % 0.5
                    val cricket2 = if (chirpCycle2 < 0.12) {
                        val env = (1.0 - chirpCycle2 / 0.12).toFloat()
                        (sin(2.0 * PI * 5200.0 * chirpPhase)).toFloat() * env * 0.25f
                    } else 0f

                    // Wind: pink noise with slow amplitude modulation
                    val white = Random.nextFloat() * 2f - 1f
                    forestPinkB[0] = 0.99886f * forestPinkB[0] + white * 0.0555179f
                    forestPinkB[1] = 0.99332f * forestPinkB[1] + white * 0.0750759f
                    forestPinkB[2] = 0.96900f * forestPinkB[2] + white * 0.1538520f
                    forestPinkB[3] = 0.86650f * forestPinkB[3] + white * 0.3104856f
                    forestPinkB[4] = 0.55000f * forestPinkB[4] + white * 0.5329522f
                    forestPinkB[5] = -0.7616f * forestPinkB[5] - white * 0.0168980f
                    val pink = forestPinkB[0] + forestPinkB[1] + forestPinkB[2] + forestPinkB[3] +
                            forestPinkB[4] + forestPinkB[5] + forestPinkB[6] + white * 0.5362f
                    forestPinkB[6] = white * 0.115926f

                    val windMod = (0.3 + 0.2 * sin(2.0 * PI * windPhase / 6.0)).toFloat()
                    val wind = pink * 0.11f * windMod

                    (wind * 0.5f + cricket1 * 0.25f + cricket2 * 0.25f) * 0.3f
                }
                generator
            }

            AmbientSound.HEARTBEAT -> {
                var phase = 0.0
                var filterState = 0f
                val dtSec = 1.0 / SAMPLE_RATE.toDouble()
                val cutoff = 150f / SAMPLE_RATE.toFloat()
                val rc = 1f / (cutoff * 2f * PI.toFloat())
                val dtf = 1f / SAMPLE_RATE.toFloat()
                val alphaF = dtf / (rc + dtf)
                val generator: () -> Float = {
                    phase += dtSec
                    // 60 BPM = 1 beat per second
                    val t = phase % 1.0

                    // Lub (first heart sound) at t=0, 60 Hz
                    val lubEnv = exp(-(t * t) / (2.0 * 0.02 * 0.02)).toFloat()
                    val lub = sin(2.0 * PI * 60.0 * t).toFloat() * lubEnv * 0.8f

                    // Dub (second heart sound) at t=0.3, 80 Hz
                    val tDub = t - 0.3
                    val dubEnv = exp(-(tDub * tDub) / (2.0 * 0.015 * 0.015)).toFloat()
                    val dub = sin(2.0 * PI * 80.0 * t).toFloat() * dubEnv * 0.6f

                    val beat = lub + dub

                    // Gentle low-pass filter to soften
                    filterState += alphaF * (beat - filterState)
                    filterState * 0.3f
                }
                generator
            }
        }
    }

    // MARK: - Volume Fade

    private fun fadeVolume(
        targetVolume: Float,
        durationMs: Long,
        onComplete: (() -> Unit)? = null
    ) {
        fadeJob?.cancel()
        if (durationMs <= 0) {
            audioTrack?.setVolume(targetVolume)
            onComplete?.invoke()
            return
        }
        fadeJob = scope.launch(Dispatchers.Main) {
            val steps = 30
            val interval = durationMs / steps
            val startVol = _volume.value.let { if (targetVolume == 0f) it else 0f }
            val delta = (targetVolume - startVol) / steps
            repeat(steps) { i ->
                delay(interval)
                val v = startVol + delta * (i + 1)
                audioTrack?.setVolume(v.coerceIn(0f, 1f))
            }
            audioTrack?.setVolume(targetVolume)
            onComplete?.invoke()
        }
    }
}
