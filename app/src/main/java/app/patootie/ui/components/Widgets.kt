package app.patootie.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.patootie.ui.theme.LocalKawaii
import kotlin.math.abs

/** A rounded speech bubble; the text pops in whenever it changes. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)
    Box(
        modifier
            .shadow(4.dp, shape, ambientColor = MaterialTheme.colorScheme.primary)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.85f) + fadeIn())
                    .togetherWith(fadeOut())
            },
            label = "speech",
        ) { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalKawaii.current.ink) }
    }
}

/** Dialog shell: a big rounded card with the mascot peeking on top. */
@Composable
fun CuteDialog(mood: Mood, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.popIn(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .padding(top = 64.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(36.dp))
                    .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
            Mascot(mood, size = 112.dp)
        }
    }
}

@Composable
fun DialogTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun CuteTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 40) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.titleMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Shows a bitmap that loads in the background; a soft placeholder until then. */
@Composable
fun AsyncIcon(key: Any, size: Dp, modifier: Modifier = Modifier, load: suspend () -> Bitmap?) {
    val image by produceState<ImageBitmap?>(null, key) { value = load()?.asImageBitmap() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Box(
                Modifier
                    .size(size * 0.8f)
                    .clip(RoundedCornerShape(size / 3))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Mascot plus a line of text, for loading / empty / error states. */
@Composable
fun MascotMessage(mood: Mood, title: String, body: String? = null, modifier: Modifier = Modifier, mascotSize: Dp = 150.dp) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(mood, size = mascotSize)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** A little "● live" pill. */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    val live = LocalKawaii.current.live
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(live.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text("● live", style = MaterialTheme.typography.labelSmall, color = live)
    }
}

/** Translucent cover with a thinking mascot while a long task runs. */
@Composable
fun BusyOverlay(message: String) {
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .popIn()
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot(Mood.Thinking, size = 120.dp)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            LoadingDots()
        }
    }
}

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dot",
    )
    Row(modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
        for (i in 0 until 3) {
            val lift = 1f - abs(t - i - 0.5f).coerceAtMost(1f)
            Box(
                Modifier
                    .offset(y = (-8 * lift).dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors[i]),
            )
        }
    }
}
