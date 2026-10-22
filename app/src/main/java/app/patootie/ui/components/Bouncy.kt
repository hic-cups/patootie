package app.patootie.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.patootie.sound.CuteSounds
import app.patootie.sound.Sfx
import kotlinx.coroutines.delay

val LocalSounds = staticCompositionLocalOf<CuteSounds?> { null }

/**
 * Click that squishes down while pressed and springs back with a wobble,
 * with a soft blip and a tiny haptic tick.
 */
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    sound: Sfx = Sfx.Tap,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow),
        label = "squish",
    )
    val sounds = LocalSounds.current
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, enabled = enabled, role = role) {
            sounds?.play(sound)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Pops into place with an overshoot, [delayMs] after first appearing. */
fun Modifier.popIn(delayMs: Long = 0): Modifier = composed {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        anim.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        val v = anim.value
        scaleX = 0.6f + 0.4f * v
        scaleY = 0.6f + 0.4f * v
        alpha = v.coerceIn(0f, 1f)
    }
}

/** The big candy pill button. */
@Composable
fun CandyButton(
    text: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    sound: Sfx = Sfx.Pop,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .bouncyClick(enabled = enabled, sound = sound, onClick = onClick)
            .shadow(if (enabled) 6.dp else 0.dp, shape, ambientColor = colors.first(), spotColor = colors.first())
            .clip(shape)
            .background(Brush.horizontalGradient(if (enabled) colors else colors.map { it.copy(alpha = 0.35f) }))
            .border(2.dp, Color.White.copy(alpha = 0.6f), shape)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, tint = contentColor)
            Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

/** Small round action bubble, for card actions. */
@Composable
fun BubbleButton(
    label: String,
    background: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .bouncyClick(onClick = onClick)
            .semantics { contentDescription = label }
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.5f))
            emoji != null -> Text(emoji, style = MaterialTheme.typography.titleMedium)
        }
    }
}
