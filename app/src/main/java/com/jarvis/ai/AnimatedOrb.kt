package com.jarvis.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val OrbBlue = Color(0xFF00BFFF)
private val OrbCyan = Color(0xFF69F5FF)
private val OrbDeepBlue = Color(0xFF174BFF)
private val OrbPurple = Color(0xFF6554FF)

@Composable
fun AnimatedOrb(
    state: JarvisState,
    level: Float = 0f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "kabir_orb")

    val idleRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_rotation"
    )

    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisState.THINKING) 1800 else 6500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val breathing by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisState.LISTENING) 350 else 1600
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val speakingPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(260),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_pulse"
    )

    val audioLevel = level.coerceIn(0f, 1f)

    val baseScale = when (state) {
        JarvisState.IDLE -> breathing
        JarvisState.LISTENING -> 1f + audioLevel * 0.18f
        JarvisState.THINKING -> breathing
        JarvisState.SPEAKING -> speakingPulse
    }

    val coreColor = when (state) {
        JarvisState.IDLE -> OrbBlue
        JarvisState.LISTENING -> OrbCyan
        JarvisState.THINKING -> OrbPurple
        JarvisState.SPEAKING -> OrbCyan
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension * 0.25f
            val center = Offset(cx, cy)

            // Outer atmospheric glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.24f),
                        OrbBlue.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 2.7f
                ),
                radius = radius * 2.7f,
                center = center
            )

            // Audio-reactive outer energy ring
            drawCircle(
                color = OrbBlue.copy(alpha = 0.24f + audioLevel * 0.45f),
                radius = radius * (1.48f + audioLevel * 0.16f),
                center = center,
                style = Stroke(width = 2.5f + audioLevel * 2f)
            )

            // Rotating orbital rings
            rotate(idleRotation, center) {
                drawOval(
                    color = OrbCyan.copy(alpha = 0.7f),
                    topLeft = Offset(cx - radius * 1.5f, cy - radius * 0.52f),
                    size = androidx.compose.ui.geometry.Size(
                        radius * 3f,
                        radius * 1.04f
                    ),
                    style = Stroke(width = 2f)
                )

                drawOval(
                    color = OrbDeepBlue.copy(alpha = 0.9f),
                    topLeft = Offset(cx - radius * 1.4f, cy - radius * 0.75f),
                    size = androidx.compose.ui.geometry.Size(
                        radius * 2.8f,
                        radius * 1.5f
                    ),
                    style = Stroke(width = 3f)
                )
            }

            rotate(ringRotation, center) {
                drawOval(
                    color = OrbCyan.copy(alpha = 0.65f),
                    topLeft = Offset(cx - radius * 1.62f, cy - radius * 0.38f),
                    size = androidx.compose.ui.geometry.Size(
                        radius * 3.24f,
                        radius * 0.76f
                    ),
                    style = Stroke(width = 1.5f)
                )

                // Orbital nodes
                for (i in 0 until 8) {
                    val angle = 2.0 * PI * i / 8.0
                    val x = cx + cos(angle).toFloat() * radius * 1.55f
                    val y = cy + sin(angle).toFloat() * radius * 0.55f

                    drawCircle(
                        color = OrbCyan,
                        radius = 3.5f,
                        center = Offset(x, y)
                    )
                }
            }

            // Particle points around the sphere
            for (i in 0 until 34) {
                val angle = i * 2.39996
                val distance = radius * (1.05f + (i % 5) * 0.15f)

                val x = cx + cos(angle).toFloat() * distance
                val y = cy + sin(angle).toFloat() * distance

                drawCircle(
                    color = OrbCyan.copy(alpha = 0.35f + (i % 3) * 0.2f),
                    radius = if (i % 4 == 0) 3f else 1.5f,
                    center = Offset(x, y)
                )
            }

            // Central energy sphere
            scale(baseScale, center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            OrbCyan,
                            OrbBlue,
                            OrbDeepBlue.copy(alpha = 0.9f),
                            Color(0xFF06132F)
                        ),
                        center = Offset(
                            cx - radius * 0.28f,
                            cy - radius * 0.32f
                        ),
                        radius = radius * 1.15f
                    ),
                    radius = radius,
                    center = center
                )

                // Inner holographic rings
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = radius * 0.7f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                drawCircle(
                    color = OrbCyan.copy(alpha = 0.9f),
                    radius = radius * 0.48f,
                    center = center,
                    style = Stroke(width = 2f)
                )

                // Fine horizontal scan lines
                for (i in -3..3) {
                    val y = cy + i * radius * 0.17f
                    val halfWidth = radius * (0.8f - kotlin.math.abs(i) * 0.08f)

                    drawLine(
                        color = OrbCyan.copy(alpha = 0.4f),
                        start = Offset(cx - halfWidth, y),
                        end = Offset(cx + halfWidth, y),
                        strokeWidth = 1f,
                        cap = StrokeCap.Round
                    )
                }

                // Bright central core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            OrbCyan.copy(alpha = 0.9f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.38f
                    ),
                    radius = radius * 0.38f,
                    center = center
                )
            }
        }
    }
}
