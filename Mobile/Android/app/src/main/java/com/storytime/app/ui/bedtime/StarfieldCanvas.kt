package com.storytime.app.ui.bedtime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseBrightness: Float,
    val twinkleSpeed: Float,
    val twinkleOffset: Float
)

private data class ShootingStar(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val speed: Float,
    val startTime: Float,
    val duration: Float
)

@Composable
fun StarfieldCanvas() {
    val stars = remember {
        List(100) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 1f,
                baseBrightness = Random.nextFloat() * 0.6f + 0.3f,
                twinkleSpeed = Random.nextFloat() * 2.5f + 0.5f,
                twinkleOffset = Random.nextFloat() * (2f * Math.PI.toFloat())
            )
        }
    }

    var elapsedTime by remember { mutableFloatStateOf(0f) }
    var shootingStar by remember { mutableStateOf<ShootingStar?>(null) }
    var nextShootingTime by remember { mutableFloatStateOf(Random.nextFloat() * 5f + 5f) }

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

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Draw twinkling stars
        for (star in stars) {
            val brightness = star.baseBrightness *
                (0.5f + 0.5f * sin(elapsedTime * star.twinkleSpeed + star.twinkleOffset))
            drawCircle(
                color = Color.White.copy(alpha = brightness),
                radius = star.size,
                center = Offset(star.x * w, star.y * h)
            )
        }

        // Draw shooting star
        shootingStar?.let { shooting ->
            val elapsed = elapsedTime - shooting.startTime
            if (elapsed in 0f..shooting.duration) {
                val progress = elapsed / shooting.duration
                val tailLength = 40f

                val headX = shooting.startX + cos(shooting.angle) * shooting.speed * progress * w
                val headY = shooting.startY + sin(shooting.angle) * shooting.speed * progress * h
                val tailX = headX - cos(shooting.angle) * tailLength
                val tailY = headY - sin(shooting.angle) * tailLength

                val fadeIn = min(progress * 5f, 1f)
                val fadeOut = if (progress > 0.7f) (1f - (progress - 0.7f) / 0.3f).coerceAtLeast(0f) else 1f
                val alpha = min(fadeIn, fadeOut) * 0.8f

                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(tailX, tailY),
                    end = Offset(headX, headY),
                    strokeWidth = 1.5f
                )

                // Bright head dot
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 2f,
                    center = Offset(headX, headY)
                )
            }
        }

        // Spawn new shooting star
        if (elapsedTime > nextShootingTime) {
            shootingStar = ShootingStar(
                startX = (Random.nextFloat() * 0.8f + 0.1f) * w,
                startY = (Random.nextFloat() * 0.35f + 0.05f) * h,
                angle = Random.nextFloat() * 0.5f + 0.3f,
                speed = Random.nextFloat() * 0.3f + 0.3f,
                startTime = elapsedTime,
                duration = Random.nextFloat() * 0.7f + 0.8f
            )
            nextShootingTime = elapsedTime + Random.nextFloat() * 7f + 8f
        }
    }
}
