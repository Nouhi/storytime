package com.storytime.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.random.Random

enum class AmbientSound(val key: String, val displayName: String) {
    WHITE_NOISE("whiteNoise", "White Noise"),
    BROWN_NOISE("brownNoise", "Brown Noise"),
    PINK_NOISE("pinkNoise", "Pink Noise"),
    SOFT_RAIN("softRain", "Soft Rain");

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
                {
                    val white = Random.nextFloat() * 2f - 1f
                    lastSample = (lastSample + 0.02f * white) / 1.02f
                    lastSample * 3.5f * 0.3f
                }
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
                {
                    val white = Random.nextFloat() * 2f - 1f
                    // Low-pass filter for rain-like sound
                    filterState += alpha * (white - filterState)
                    // Occasional louder drops
                    val drop = if (Random.nextFloat() < 0.001f) Random.nextFloat() * 0.3f + 0.3f else 0f
                    (filterState * 1.5f + drop) * 0.3f
                }
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
