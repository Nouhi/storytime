package com.storytime.app.ui.bedtime

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytime.app.audio.AmbientSound
import com.storytime.app.audio.AmbientSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimeScreen(
    audioManager: AmbientSoundManager,
    initialSound: AmbientSound = AmbientSound.WHITE_NOISE,
    onSoundChanged: (String) -> Unit = {},
    onExit: () -> Unit
) {
    val isPlaying by audioManager.isPlaying.collectAsState()
    val currentSound by audioManager.currentSound.collectAsState()
    val volume by audioManager.volume.collectAsState()

    var showHint by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    // Save original brightness
    val originalBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: -1f
    }

    // Clock state
    var timeText by remember { mutableStateOf(currentTimeString()) }

    // Update clock every minute
    LaunchedEffect(Unit) {
        while (isActive) {
            timeText = currentTimeString()
            val now = System.currentTimeMillis()
            val nextMinute = 60_000L - (now % 60_000L)
            delay(nextMinute)
        }
    }

    // Dim screen and start audio on enter
    LaunchedEffect(Unit) {
        // Dim screen
        activity?.window?.let { window ->
            val params = window.attributes
            params.screenBrightness = 0.01f
            window.attributes = params
        }

        // Start ambient sound
        audioManager.play(initialSound, fadeDurationMs = 3000)

        // Hide hint after 3 seconds
        delay(3000)
        showHint = false
    }

    // Keep screen on + restore brightness on exit
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            audioManager.stop(fadeDurationMs = 1000)
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = originalBrightness
                window.attributes = params
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onExit() }
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.weight(1f))

            // Clock
            Text(
                text = timeText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(32.dp))

            // Sound name + play/pause
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = currentSound.displayName,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = {
                    if (isPlaying) audioManager.stop()
                    else audioManager.play(currentSound)
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Volume slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
                Slider(
                    value = volume,
                    onValueChange = { audioManager.setVolume(it) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White.copy(alpha = 0.4f),
                        activeTrackColor = Color.White.copy(alpha = 0.3f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Sound picker chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(AmbientSound.entries) { sound ->
                    FilterChip(
                        selected = currentSound == sound,
                        onClick = {
                            onSoundChanged(sound.key)
                            audioManager.play(sound, fadeDurationMs = 1000)
                        },
                        label = { Text(sound.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            selectedContainerColor = Color.White.copy(alpha = 0.15f),
                            labelColor = Color.White.copy(alpha = 0.6f),
                            selectedLabelColor = Color.White.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Hint
            AnimatedVisibility(
                visible = showHint,
                exit = fadeOut()
            ) {
                Text(
                    text = "Tap anywhere to exit",
                    color = Color.White.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun currentTimeString(): String {
    val cal = Calendar.getInstance()
    val h = cal.get(Calendar.HOUR)
    val m = cal.get(Calendar.MINUTE)
    return "%d:%02d".format(if (h == 0) 12 else h, m)
}
