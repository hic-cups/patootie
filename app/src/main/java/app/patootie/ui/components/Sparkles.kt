package app.patootie.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.patootie.ui.theme.Kawaii
import app.patootie.ui.theme.LocalKawaii
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class Bit { Star, Heart, Dot }

private class Floaty(
    val x: Float, val y: Float, val speed: Float, val size: Float,
    val phase: Float, val sway: Float, val kind: Bit, val color: Color,
)

/** Hearts and sparkles drifting gently upward behind the content. */
@Composable
fun SparkleField(modifier: Modifier = Modifier, count: Int = 22) {
    val colors = LocalKawaii.current
    val bits = remember(count) {
        val rnd = Random(7)
        List(count) {
            val kind = Bit.entries[rnd.nextInt(Bit.entries.size)]
            Floaty(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                speed = 0.3f + rnd.nextFloat() * 0.7f,
                size = 5f + rnd.nextFloat() * 9f,
                phase = rnd.nextFloat(),
                sway = 6f + rnd.nextFloat() * 14f,
                kind = kind,
                color = when (kind) {
                    Bit.Star -> if (rnd.nextBoolean()) colors.sparkle else Kawaii.Lavender
                    Bit.Heart -> if (rnd.nextBoolean()) Kawaii.Pink else colors.heart
                    Bit.Dot -> Kawaii.Mint
                },
            )
        }
    }
    val t by rememberInfiniteTransition(label = "sparkles").animateFloat(
        0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "drift",
    )
    Canvas(modifier) {
        for (b in bits) {
            val y = ((b.y - t * b.speed) % 1f + 1f) % 1f
            val x = b.x * size.width + sin((t * 6 + b.phase) * 2 * PI).toFloat() * b.sway
            val twinkle = 0.25f + 0.55f * abs(sin((t * 14 + b.phase) * PI).toFloat())
            val c = Offset(x, y * size.height)
            val s = b.size * density / 2f
            when (b.kind) {
                Bit.Star -> drawSparkle(c, s, b.color.copy(alpha = twinkle))
                Bit.Heart -> drawHeart(c, s, b.color.copy(alpha = twinkle * 0.8f))
                Bit.Dot -> drawCircle(b.color.copy(alpha = twinkle), s * 0.35f, c)
            }
        }
    }
}

/**
 * Hearts and sparkles bursting out from the center each time [trigger] changes.
 */
@Composable
fun HeartBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val colors = LocalKawaii.current
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val rays = remember(trigger) {
        val rnd = Random(trigger)
        List(16) { i ->
            Triple(
                (i / 16f) * 2 * PI.toFloat() + rnd.nextFloat() * 0.3f,
                0.6f + rnd.nextFloat() * 0.5f,
                i % 3,
            )
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val reach = size.minDimension * 0.55f
        for ((angle, dist, kind) in rays) {
            val r = reach * dist * p
            val c = center + Offset(cos(angle) * r, sin(angle) * r - p * p * reach * 0.15f)
            val alpha = (1f - p).coerceIn(0f, 1f)
            val s = (6f + 10f * (1f - p)) * density / 2f + 4f * density
            when (kind) {
                0 -> drawHeart(c, s, colors.heart.copy(alpha = alpha))
                1 -> drawSparkle(c, s, colors.sparkle.copy(alpha = alpha))
                else -> drawHeart(c, s * 0.7f, Kawaii.Lavender.copy(alpha = alpha))
            }
        }
    }
}

fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s)
        cubicTo(c.x - s * 1.6f, c.y - s * 0.1f, c.x - s * 0.9f, c.y - s * 1.3f, c.x, c.y - s * 0.45f)
        cubicTo(c.x + s * 0.9f, c.y - s * 1.3f, c.x + s * 1.6f, c.y - s * 0.1f, c.x, c.y + s)
        close()
    }
    drawPath(p, color)
}

/** A four-pointed twinkle. */
fun DrawScope.drawSparkle(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - s * 1.3f)
        quadraticTo(c.x, c.y, c.x + s * 1.3f, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + s * 1.3f)
        quadraticTo(c.x, c.y, c.x - s * 1.3f, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - s * 1.3f)
        close()
    }
    drawPath(p, color)
}
