package app.patootie.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.patootie.ui.theme.Kawaii
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Mood { Happy, Thinking, Excited, Sad }

/**
 * Patootie, the mochi bunny. Bobs, blinks and wiggles on its own; [mood] changes
 * the face, the ears and how bouncy it is.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val loop = rememberInfiniteTransition(label = "mascot")
    val period = when (mood) {
        Mood.Excited -> 520
        Mood.Thinking -> 1400
        Mood.Sad -> 2600
        Mood.Happy -> 1800
    }
    val t by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "beat",
    )
    val wave = sin(t * 2 * PI).toFloat()

    // Ears droop when sad and perk up when excited, with a springy settle.
    val earDroop by animateFloatAsState(
        when (mood) { Mood.Sad -> 34f; Mood.Thinking -> 10f; Mood.Excited -> -6f; Mood.Happy -> 0f },
        spring(dampingRatio = 0.4f, stiffness = 120f), label = "ears",
    )

    val blink = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        if (mood == Mood.Excited) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(1800, 4200))
            blink.animateTo(0.08f, tween(70))
            blink.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                val hop = when (mood) {
                    Mood.Excited -> -kotlin.math.abs(wave) * 0.16f
                    Mood.Sad -> wave * 0.01f
                    else -> wave * 0.035f
                }
                translationY = hop * this.size.height
                // Squash on landing, stretch in the air.
                val squash = if (mood == Mood.Excited) 1f - (1f - kotlin.math.abs(wave)) * 0.08f else 1f + wave * 0.015f
                scaleY = squash
                scaleX = 2f - squash
                rotationZ = if (mood == Mood.Thinking) wave * 6f else 0f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
    ) {
        drawMascot(mood, earDroop, wiggle = wave, eyeOpen = blink.value, t = t)
    }
}

private val Ink = Kawaii.LavenderInk
private val Body = Color(0xFFFFFDFE)

private fun DrawScope.drawMascot(mood: Mood, earDroop: Float, wiggle: Float, eyeOpen: Float, t: Float) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.022f
    val headCenter = Offset(w * 0.5f, h * 0.64f)
    val headSize = Size(w * 0.78f, h * 0.56f)

    // Ears, behind the head. Left one gets the mint bow.
    for (side in listOf(-1f, 1f)) {
        val pivot = Offset(w * 0.5f + side * w * 0.17f, h * 0.42f)
        val angle = side * (12f + earDroop + if (mood == Mood.Happy) wiggle * 3f * side else 0f)
        rotate(angle, pivot) {
            val ear = Size(w * 0.2f, h * 0.38f)
            val topLeft = Offset(pivot.x - ear.width / 2, pivot.y - ear.height * 0.92f)
            drawOval(Kawaii.Lavender, topLeft, ear)
            drawOval(Ink, topLeft, ear, style = Stroke(stroke))
            val inner = Size(ear.width * 0.46f, ear.height * 0.66f)
            drawOval(
                Kawaii.Pink,
                Offset(pivot.x - inner.width / 2, topLeft.y + ear.height * 0.14f),
                inner,
            )
            if (side < 0) drawBow(Offset(pivot.x, topLeft.y + ear.height * 0.62f), w * 0.075f)
        }
    }

    // Mochi head.
    val headTopLeft = Offset(headCenter.x - headSize.width / 2, headCenter.y - headSize.height / 2)
    drawOval(Color(0x22000000), headTopLeft + Offset(0f, h * 0.025f), headSize)
    drawOval(Body, headTopLeft, headSize)
    drawOval(Ink, headTopLeft, headSize, style = Stroke(stroke))

    // Tiny paws.
    for (side in listOf(-1f, 1f)) {
        val paw = Size(w * 0.13f, h * 0.08f)
        val c = Offset(headCenter.x + side * w * 0.17f, headCenter.y + headSize.height * 0.42f)
        drawOval(Body, c - Offset(paw.width / 2, paw.height / 2), paw)
        drawOval(Ink, c - Offset(paw.width / 2, paw.height / 2), paw, style = Stroke(stroke * 0.8f))
    }

    // Blush.
    val blushAlpha = if (mood == Mood.Excited) 0.95f else 0.7f
    for (side in listOf(-1f, 1f)) {
        val blush = Size(w * 0.13f, h * 0.06f)
        val c = Offset(headCenter.x + side * w * 0.24f, headCenter.y + h * 0.06f)
        drawOval(Kawaii.Pink.copy(alpha = blushAlpha), c - Offset(blush.width / 2, blush.height / 2), blush)
    }

    // Eyes.
    val eyeY = headCenter.y - h * 0.02f
    val lookX = if (mood == Mood.Thinking) sin(t * 2 * PI).toFloat() * w * 0.02f else 0f
    for (side in listOf(-1f, 1f)) {
        val c = Offset(headCenter.x + side * w * 0.15f + lookX, eyeY)
        when (mood) {
            Mood.Excited -> {
                // Happy ^ ^ eyes.
                val p = Path().apply {
                    moveTo(c.x - w * 0.055f, c.y + h * 0.02f)
                    quadraticTo(c.x, c.y - h * 0.06f, c.x + w * 0.055f, c.y + h * 0.02f)
                }
                drawPath(p, Ink, style = Stroke(stroke * 1.4f, cap = StrokeCap.Round))
            }
            else -> {
                val eye = Size(w * 0.095f, h * 0.12f * eyeOpen.coerceAtLeast(0.08f))
                drawOval(Ink, c - Offset(eye.width / 2, eye.height / 2), eye)
                if (eyeOpen > 0.5f) {
                    drawCircle(Color.White, w * 0.018f, c + Offset(-w * 0.015f, -h * 0.025f))
                    drawCircle(Color.White, w * 0.009f, c + Offset(w * 0.018f, h * 0.022f))
                }
                if (mood == Mood.Sad) {
                    val brow = Path().apply {
                        moveTo(c.x - side * w * 0.06f, c.y - h * 0.1f)
                        lineTo(c.x + side * w * 0.04f, c.y - h * 0.075f)
                    }
                    drawPath(brow, Ink, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
    }

    // Mouth.
    val m = Offset(headCenter.x, headCenter.y + h * 0.075f)
    val mouthStroke = Stroke(stroke * 1.1f, cap = StrokeCap.Round)
    when (mood) {
        Mood.Happy -> {
            // Cat-like "ω".
            val p = Path().apply {
                moveTo(m.x - w * 0.05f, m.y - h * 0.01f)
                quadraticTo(m.x - w * 0.025f, m.y + h * 0.035f, m.x, m.y)
                quadraticTo(m.x + w * 0.025f, m.y + h * 0.035f, m.x + w * 0.05f, m.y - h * 0.01f)
            }
            drawPath(p, Ink, style = mouthStroke)
        }
        Mood.Excited -> {
            val mouth = Size(w * 0.1f, h * 0.075f)
            drawOval(Ink, m - Offset(mouth.width / 2, mouth.height * 0.3f), mouth)
            drawOval(
                Kawaii.PinkDeep,
                m + Offset(-mouth.width * 0.3f, mouth.height * 0.25f),
                Size(mouth.width * 0.6f, mouth.height * 0.4f),
            )
        }
        Mood.Thinking -> {
            val p = Path().apply {
                moveTo(m.x - w * 0.04f, m.y)
                cubicTo(m.x - w * 0.015f, m.y - h * 0.02f, m.x + w * 0.015f, m.y + h * 0.02f, m.x + w * 0.04f, m.y)
            }
            drawPath(p, Ink, style = mouthStroke)
        }
        Mood.Sad -> {
            val p = Path().apply {
                moveTo(m.x - w * 0.04f, m.y + h * 0.02f)
                quadraticTo(m.x, m.y - h * 0.025f, m.x + w * 0.04f, m.y + h * 0.02f)
            }
            drawPath(p, Ink, style = mouthStroke)
            // A single tear that slides down and refills.
            val tear = Offset(headCenter.x + w * 0.16f, eyeY + h * 0.07f + t * h * 0.08f)
            drawTear(tear, w * 0.03f, alpha = 1f - t)
        }
    }
}

private fun DrawScope.drawBow(c: Offset, r: Float) {
    val left = Path().apply {
        moveTo(c.x, c.y)
        lineTo(c.x - r * 1.5f, c.y - r)
        quadraticTo(c.x - r * 1.9f, c.y, c.x - r * 1.5f, c.y + r)
        close()
    }
    val right = Path().apply {
        moveTo(c.x, c.y)
        lineTo(c.x + r * 1.5f, c.y - r)
        quadraticTo(c.x + r * 1.9f, c.y, c.x + r * 1.5f, c.y + r)
        close()
    }
    drawPath(left, Kawaii.MintDeep)
    drawPath(right, Kawaii.MintDeep)
    drawCircle(Kawaii.Mint, r * 0.5f, c)
}

private fun DrawScope.drawTear(c: Offset, r: Float, alpha: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 1.8f)
        quadraticTo(c.x + r * 1.1f, c.y - r * 0.2f, c.x, c.y + r)
        quadraticTo(c.x - r * 1.1f, c.y - r * 0.2f, c.x, c.y - r * 1.8f)
        close()
    }
    drawPath(p, Color(0xFF9FD3FF).copy(alpha = alpha.coerceIn(0f, 1f)))
}
