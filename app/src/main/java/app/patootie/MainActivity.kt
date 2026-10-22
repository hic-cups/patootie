package app.patootie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.patootie.data.Clone
import app.patootie.sound.Sfx
import app.patootie.ui.MainViewModel
import app.patootie.ui.RootState
import app.patootie.ui.Screen
import app.patootie.ui.components.BusyOverlay
import app.patootie.ui.components.CandyButton
import app.patootie.ui.components.LoadingDots
import app.patootie.ui.components.LocalSounds
import app.patootie.ui.components.MascotMessage
import app.patootie.ui.components.Mood
import app.patootie.ui.components.SparkleField
import app.patootie.ui.screens.CreateScreen
import app.patootie.ui.screens.DeleteDialog
import app.patootie.ui.screens.HelpDialog
import app.patootie.ui.screens.HomeActions
import app.patootie.ui.screens.HomeScreen
import app.patootie.ui.screens.PickAppScreen
import app.patootie.ui.screens.RenameDialog
import app.patootie.ui.screens.UpdateDialog
import app.patootie.ui.theme.PatootieTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PatootieTheme {
                CompositionLocalProvider(LocalSounds provides vm.sounds) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SparkleField(Modifier.fillMaxSize())
                        PatootieRoot(vm)
                    }
                }
            }
        }
    }
}

private sealed interface Pending {
    data class Rename(val clone: Clone) : Pending
    data class Update(val clone: Clone) : Pending
    data class Delete(val clone: Clone) : Pending
    data object Help : Pending
}

@Composable
private fun PatootieRoot(vm: MainViewModel) {
    when (vm.root) {
        RootState.Checking -> CenterColumn {
            MascotMessage(Mood.Thinking, "Looking for root magic…")
            LoadingDots()
        }
        RootState.Missing -> CenterColumn {
            MascotMessage(
                Mood.Sad,
                "I need root to keep clones apart",
                "Grant Patootie root in Magisk, KernelSU or APatch, then tap retry. " +
                    "Without it I can't tuck each clone's data away safely.",
            )
            Spacer(Modifier.height(16.dp))
            CandyButton("Retry", sound = Sfx.Tap, onClick = vm::checkRoot)
        }
        RootState.Granted -> Main(vm)
    }
}

@Composable
private fun Main(vm: MainViewModel) {
    val clones by vm.clones.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pending?>(null) }
    var soundOn by remember { mutableStateOf(vm.sounds.enabled) }

    // Coming back from a clone: the live slot may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }

    AnimatedContent(
        targetState = vm.screen,
        contentKey = { it::class },
        transitionSpec = {
            val forward = targetState !is Screen.Home
            (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) { w -> if (forward) w / 3 else -w / 3 } +
                fadeIn() + scaleIn(initialScale = 0.96f))
                .togetherWith(slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
        },
        label = "nav",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(
                clones = clones,
                status = status,
                speech = vm.speech,
                soundOn = soundOn,
                actions = HomeActions(
                    newClone = { vm.go(Screen.PickApp) },
                    launch = vm::launch,
                    launchOriginal = vm::launchOriginal,
                    rename = { pending = Pending.Rename(it) },
                    update = { pending = Pending.Update(it) },
                    pin = vm::pin,
                    delete = { pending = Pending.Delete(it) },
                    finishInterrupted = vm::finishInterrupted,
                    toggleSound = { vm.toggleSound(); soundOn = vm.sounds.enabled },
                    help = { pending = Pending.Help },
                    icon = { vm.iconFor(it.packageName, it.tint, it.sticker) },
                    originalIcon = { vm.iconFor(it, null, null) },
                ),
            )
            Screen.PickApp -> PickAppScreen(
                apps = vm.installed,
                icon = vm::appIcon,
                onBack = vm::back,
                onPick = { vm.go(Screen.Create(it)) },
            )
            is Screen.Create -> CreateScreen(
                app = screen.app,
                state = vm.createState,
                defaultName = { vm.defaultName(screen.app) },
                preview = { tint, sticker -> vm.iconFor(screen.app.packageName, tint, sticker) },
                onBack = vm::back,
                onClone = { name, tint, sticker -> vm.create(screen.app, name, tint, sticker) },
                onRetry = vm::retryCreate,
                onLaunch = { vm.launch(it); vm.go(Screen.Home) },
                onPin = vm::pin,
                onDone = { vm.go(Screen.Home) },
            )
        }
    }

    when (val p = pending) {
        is Pending.Rename -> RenameDialog(p.clone, { pending = null }) { vm.rename(p.clone, it); pending = null }
        is Pending.Update -> UpdateDialog(p.clone, { pending = null }) { vm.updateFromOriginal(p.clone); pending = null }
        is Pending.Delete -> DeleteDialog(p.clone, { pending = null }) { vm.delete(p.clone); pending = null }
        Pending.Help -> HelpDialog { pending = null }
        null -> Unit
    }
    vm.busy?.let { BusyOverlay(it) }
}

@Composable
private fun CenterColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) { content() }
}
