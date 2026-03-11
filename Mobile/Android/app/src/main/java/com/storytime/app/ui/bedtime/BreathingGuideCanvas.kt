package com.storytime.app.ui.bedtime

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytime.app.R
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.pow
import kotlin.math.sin

// 4-7-8 breathing pattern: 4s inhale, 7s hold, 8s exhale = 19s total
private const val INHALE_DURATION = 4f
private const val HOLD_DURATION = 7f
private const val EXHALE_DURATION = 8f
private const val TOTAL_CYCLE = INHALE_DURATION + HOLD_DURATION + EXHALE_DURATION

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 1.0f
private const val CIRCLE_RADIUS_DP = 75f

private fun easeInOut(t: Float): Float {
    return if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
}

private data class BreathingState(
    val scale: Float,
    val opacity: Float,
    @StringRes val labelRes: Int
)

private fun computeState(cycleTime: Float): BreathingState {
    return when {
        cycleTime < INHALE_DURATION -> {
            val progress = cycleTime / INHALE_DURATION
            val eased = easeInOut(progress)
            val scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * eased
            BreathingState(scale, 0.8f + 0.2f * eased, R.string.breathing_in)
        }
        cycleTime < INHALE_DURATION + HOLD_DURATION -> {
            val holdProgress = (cycleTime - INHALE_DURATION) / HOLD_DURATION
            val pulse = 0.8f + 0.2f * sin(holdProgress * Math.PI.toFloat() * 2f)
            BreathingState(MAX_SCALE, pulse, R.string.breathing_hold)
        }
        else -> {
            val exhaleProgress = (cycleTime - INHALE_DURATION - HOLD_DURATION) / EXHALE_DURATION
            val eased = easeInOut(exhaleProgress)
            val scale = MAX_SCALE - (MAX_SCALE - MIN_SCALE) * eased
            BreathingState(scale, 0.8f - 0.2f * eased, R.string.breathing_out)
        }
    }
}

@Composable
fun BreathingGuideCanvas() {
    var elapsedTime by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = 0L
        while (true) {
            val frameTime = awaitFrame()
            if (lastFrameTime > 0) {
                val delta = (frameTime - lastFrameTime) / 1_000_000_000f
                elapsedTime += delta
            }
            lastFrameTime = frameTime
        }
    }

    val cycleTime = elapsedTime % TOTAL_CYCLE
    val state = computeState(cycleTime)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((CIRCLE_RADIUS_DP * 2 + 20).dp)) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val maxRadius = CIRCLE_RADIUS_DP.dp.toPx()

            // Outer glow ring
            drawCircle(
                color = Color.White.copy(alpha = state.opacity * 0.1f),
                radius = maxRadius + 10.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 8.dp.toPx())
            )

            // Main breathing circle
            drawCircle(
                color = Color.White.copy(alpha = state.opacity * 0.3f),
                radius = maxRadius * state.scale,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner fill
            drawCircle(
                color = Color.White.copy(alpha = state.opacity * 0.05f),
                radius = maxRadius * state.scale,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }

        // Phase label below the circle
        Text(
            text = stringResource(state.labelRes),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (CIRCLE_RADIUS_DP + 30).dp)
        )
    }
}
