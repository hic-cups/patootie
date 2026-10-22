package app.patootie.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.patootie.apps.InstalledApp
import app.patootie.data.Clone
import app.patootie.icon.Stickers
import app.patootie.icon.Tints
import app.patootie.sound.Sfx
import app.patootie.ui.CreateState
import app.patootie.ui.components.AsyncIcon
import app.patootie.ui.components.BubbleButton
import app.patootie.ui.components.CandyButton
import app.patootie.ui.components.CuteTextField
import app.patootie.ui.components.HeartBurst
import app.patootie.ui.components.LoadingDots
import app.patootie.ui.components.MascotMessage
import app.patootie.ui.components.Mood
import app.patootie.ui.components.bouncyClick
import app.patootie.ui.components.popIn
import app.patootie.ui.theme.LocalKawaii

@Composable
fun CreateScreen(
    app: InstalledApp,
    state: CreateState,
    defaultName: suspend () -> String,
    preview: suspend (tint: Int?, sticker: String?) -> Bitmap?,
    onBack: () -> Unit,
    onClone: (name: String, tint: Int?, sticker: String?) -> Unit,
    onRetry: () -> Unit,
    onLaunch: (Clone) -> Unit,
    onPin: (Clone) -> Unit,
    onDone: () -> Unit,
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), initialScale = 0.8f) + fadeIn())
                .togetherWith(fadeOut(tween(120)))
        },
        label = "create",
        modifier = Modifier.fillMaxSize(),
    ) { s ->
        when (s) {
            CreateState.Editing -> Editor(app, defaultName, preview, onBack, onClone)
            CreateState.Working -> Centered {
                MascotMessage(Mood.Thinking, "Sprinkling clone magic…", "Making a cozy new home for ${app.label}")
                LoadingDots()
            }
            is CreateState.Done -> Success(s.clone, onLaunch, onPin, onDone)
            is CreateState.Failed -> Centered {
                MascotMessage(Mood.Sad, "Oh no, that didn't work", s.message)
                Spacer(Modifier.height(16.dp))
                CandyButton("Try again", sound = Sfx.Tap, onClick = onRetry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(
    app: InstalledApp,
    defaultName: suspend () -> String,
    preview: suspend (Int?, String?) -> Bitmap?,
    onBack: () -> Unit,
    onClone: (String, Int?, String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    var tintIndex by rememberSaveable { mutableIntStateOf(1) }
    var stickerIndex by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(app.packageName) { if (!nameTouched) name = defaultName() }
    val tint = Tints.all[tintIndex].second
    val sticker = Stickers.all[stickerIndex]

    val bob by rememberInfiniteTransition(label = "preview").animateFloat(
        -6f, 6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob",
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BubbleButton(
                "Back",
                MaterialTheme.colorScheme.primaryContainer,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Text("New ${app.label} clone", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            AsyncIcon(
                key = Triple(app.packageName, tint, sticker),
                size = 112.dp,
                modifier = Modifier.graphicsLayer { translationY = bob * density },
            ) { preview(tint, sticker) }
        }

        CuteTextField(name, { name = it; nameTouched = true }, label = "Clone name")

        Section("Icon tint")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tints.all.forEachIndexed { i, (label, color) ->
                Swatch(label, color, selected = i == tintIndex) { tintIndex = i }
            }
        }

        Section("Sticker")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stickers.all.forEachIndexed { i, s -> StickerChip(s, selected = i == stickerIndex) { stickerIndex = i } }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Clones take turns 💤 Opening one tucks the others away, so each keeps its own login. " +
                "Only the one you opened last gets notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        CandyButton(
            "Clone!",
            icon = Icons.Default.Check,
            sound = Sfx.Sparkle,
            enabled = name.isNotBlank(),
            modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 24.dp),
            onClick = { onClone(name, tint, sticker) },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun Swatch(label: String, color: Int?, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "swatch")
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .bouncyClick(onClick = onClick)
                .size(44.dp)
                .border(3.dp, ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Text("∅", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StickerChip(sticker: String?, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "sticker")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bouncyClick(onClick = onClick)
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(sticker ?: "∅", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Success(clone: Clone, onLaunch: (Clone) -> Unit, onPin: (Clone) -> Unit, onDone: () -> Unit) {
    var burst by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(clone.id) { burst++ }
    Box(Modifier.fillMaxSize()) {
        HeartBurst(burst, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MascotMessage(Mood.Excited, "Yay! ${clone.name} is ready ✨", "A brand-new, logged-out copy of ${clone.appLabel}, just for you.")
            Spacer(Modifier.height(20.dp))
            CandyButton(
                "Open it now",
                icon = Icons.Default.PlayArrow,
                colors = listOf(LocalKawaii.current.live, MaterialTheme.colorScheme.secondary),
                modifier = Modifier.popIn(200),
                onClick = { onLaunch(clone) },
            )
            Spacer(Modifier.height(12.dp))
            CandyButton("Add to home screen 📌", sound = Sfx.Sparkle, modifier = Modifier.popIn(320), onClick = { onPin(clone) })
            Spacer(Modifier.height(12.dp))
            Text(
                "Back to my clones",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.popIn(440).bouncyClick(onClick = onDone).padding(12.dp),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
