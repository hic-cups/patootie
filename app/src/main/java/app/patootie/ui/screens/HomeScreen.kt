package app.patootie.ui.screens

import android.graphics.Bitmap

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.patootie.data.Clone
import app.patootie.engine.PackageStatus
import app.patootie.engine.SwapScripts
import app.patootie.sound.Sfx
import app.patootie.ui.Speech
import app.patootie.ui.components.AsyncIcon
import app.patootie.ui.components.BubbleButton
import app.patootie.ui.components.CandyButton
import app.patootie.ui.components.LivePill
import app.patootie.ui.components.Mascot
import app.patootie.ui.components.MascotMessage
import app.patootie.ui.components.Mood
import app.patootie.ui.components.SpeechBubble
import app.patootie.ui.components.bouncyClick
import app.patootie.ui.components.popIn
import app.patootie.ui.theme.LocalKawaii

/** Callbacks the home screen needs; keeps the screen free of the ViewModel. */
class HomeActions(
    val newClone: () -> Unit,
    val launch: (Clone) -> Unit,
    val launchOriginal: (String) -> Unit,
    val rename: (Clone) -> Unit,
    val update: (Clone) -> Unit,
    val pin: (Clone) -> Unit,
    val delete: (Clone) -> Unit,
    val finishInterrupted: (String) -> Unit,
    val toggleSound: () -> Unit,
    val help: () -> Unit,
    val icon: suspend (Clone) -> Bitmap?,
    val originalIcon: suspend (String) -> Bitmap?,
)

@Composable
fun HomeScreen(
    clones: List<Clone>,
    status: Map<String, PackageStatus>,
    speech: Speech,
    soundOn: Boolean,
    actions: HomeActions,
) {
    val groups = clones.groupBy { it.packageName }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item(key = "header") { Header(speech, soundOn, actions) }

            val interrupted = status.values.filter { it.interrupted }
            items(interrupted, key = { "fix-" + it.packageName }) { s ->
                FixBanner(groups[s.packageName]?.firstOrNull()?.appLabel ?: s.packageName) {
                    actions.finishInterrupted(s.packageName)
                }
            }

            if (clones.isEmpty()) {
                item(key = "empty") {
                    MascotMessage(
                        Mood.Happy,
                        title = "No clones yet!",
                        body = "Tap “New clone” and pick an app. Each clone gets its very own login and data 💖",
                        modifier = Modifier.fillMaxWidth().popIn(150),
                    )
                }
            }

            groups.forEach { (pkg, list) ->
                val live = status[pkg]?.activeSlot ?: SwapScripts.ORIGINAL
                item(key = "group-$pkg") {
                    GroupHeader(list.first(), originalLive = live == SwapScripts.ORIGINAL, actions)
                }
                items(list, key = { it.id }) { clone ->
                    CloneCard(
                        clone = clone,
                        live = live == clone.id,
                        sizeKb = status[pkg]?.slotSizesKb?.get(clone.id),
                        actions = actions,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        CandyButton(
            "New clone",
            icon = Icons.Default.Add,
            sound = Sfx.Pop,
            onClick = actions.newClone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .popIn(300),
        )
    }
}

@Composable
private fun Header(speech: Speech, soundOn: Boolean, actions: HomeActions) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Patootie",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            BubbleButton(
                if (soundOn) "Turn sounds off" else "Turn sounds on",
                MaterialTheme.colorScheme.secondaryContainer,
                emoji = if (soundOn) "🔔" else "🔕",
                onClick = actions.toggleSound,
            )
            Spacer(Modifier.width(8.dp))
            BubbleButton("How it works", MaterialTheme.colorScheme.primaryContainer, emoji = "❔", onClick = actions.help)
        }
        Text(
            "your little clone garden ✿",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Mascot(speech.mood, size = 104.dp)
            SpeechBubble(speech.text, Modifier.padding(start = 4.dp, bottom = 40.dp).weight(1f))
        }
    }
}

@Composable
private fun FixBanner(appLabel: String, onFix: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .bouncyClick(onClick = onFix)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🩹", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("A swap for $appLabel got interrupted", style = MaterialTheme.typography.titleSmall)
            Text("Your data is safe. Tap and I'll finish it!", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GroupHeader(sample: Clone, originalLive: Boolean, actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncIcon(sample.packageName, 30.dp) { actions.originalIcon(sample.packageName) }
        Spacer(Modifier.width(8.dp))
        Text(
            sample.appLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (originalLive) LivePill(Modifier.padding(end = 8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .bouncyClick { actions.launchOriginal(sample.packageName) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Home, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CloneCard(clone: Clone, live: Boolean, sizeKb: Long?, actions: HomeActions, modifier: Modifier = Modifier) {
    val k = LocalKawaii.current
    val shape = RoundedCornerShape(30.dp)
    // The live clone's icon does a happy little wiggle.
    val wiggle by rememberInfiniteTransition(label = "wiggle").animateFloat(
        -4f, 4f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "w",
    )
    var showActions by remember { mutableStateOf(false) }
    Column(
        modifier
            .popIn()
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(k.cardTop, k.cardBottom)))
            .border(2.dp, if (live) k.live.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
            .bouncyClick(sound = Sfx.Tap) { showActions = !showActions }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncIcon(
                key = Triple(clone.id, clone.tint, clone.sticker) to clone.appVersion,
                size = 60.dp,
                modifier = Modifier.graphicsLayer { rotationZ = if (live) wiggle else 0f },
            ) { actions.icon(clone) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    clone.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) LivePill(Modifier.padding(end = 6.dp))
                    Text(
                        listOfNotNull("v${clone.appVersion}", sizeKb?.let(::prettySize)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            BubbleButton(
                if (showActions) "Hide options" else "Options",
                MaterialTheme.colorScheme.surfaceVariant,
                icon = Icons.Default.MoreVert,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 40.dp,
                onClick = { showActions = !showActions },
            )
            Spacer(Modifier.width(8.dp))
            BubbleButton(
                "Launch ${clone.name}",
                MaterialTheme.colorScheme.secondary,
                icon = Icons.Default.PlayArrow,
                tint = MaterialTheme.colorScheme.onSecondary,
                size = 52.dp,
                onClick = { actions.launch(clone) },
            )
        }
        if (showActions) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).popIn(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LabeledBubble("Rename", "✏️") { actions.rename(clone) }
                LabeledBubble("Update", null, icon = true) { actions.update(clone) }
                LabeledBubble("Home icon", "📌") { actions.pin(clone) }
                LabeledBubble("Delete", "🗑️", danger = true) { actions.delete(clone) }
            }
        }
    }
}

@Composable
private fun LabeledBubble(label: String, emoji: String?, icon: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BubbleButton(
            label,
            if (danger) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            emoji = emoji,
            icon = if (icon) Icons.Default.Refresh else null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onClick,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun prettySize(kb: Long): String = when {
    kb >= 1024 * 1024 -> "%.1f GB".format(kb / 1024.0 / 1024.0)
    kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
    else -> "$kb KB"
}
