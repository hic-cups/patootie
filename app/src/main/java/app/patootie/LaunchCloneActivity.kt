package app.patootie

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.patootie.engine.SwapScripts
import app.patootie.sound.Sfx
import app.patootie.ui.MainViewModel
import app.patootie.ui.components.CandyButton
import app.patootie.ui.components.HeartBurst
import app.patootie.ui.components.LoadingDots
import app.patootie.ui.components.LocalSounds
import app.patootie.ui.components.MascotMessage
import app.patootie.ui.components.Mood
import app.patootie.ui.components.SparkleField
import app.patootie.ui.components.popIn
import app.patootie.ui.theme.PatootieTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The little card that pops up over the home screen: swaps the clone's data in,
 * celebrates, then opens the app. Shortcuts and the in-app Launch button use it.
 */
class LaunchCloneActivity : ComponentActivity() {

    private sealed interface Phase {
        data class Swapping(val name: String) : Phase
        data class Ready(val name: String) : Phase
        data class Failed(val message: String) : Phase
    }

    private var phase by mutableStateOf<Phase>(Phase.Swapping(""))
    private var burst by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PatootieApp
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val slot = intent.getStringExtra(EXTRA_SLOT)
        if (pkg == null || slot == null) {
            finish()
            return
        }

        setContent {
            PatootieTheme {
                CompositionLocalProvider(LocalSounds provides app.sounds) {
                    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .popIn()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(40.dp))
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            SparkleField(Modifier.matchParentSize(), count = 12)
                            HeartBurst(burst, Modifier.matchParentSize())
                            AnimatedContent(phase, contentKey = { it::class }, label = "phase") { p ->
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    when (p) {
                                        is Phase.Swapping -> {
                                            MascotMessage(Mood.Thinking, "Swapping in ${p.name}…", "Tucking the others in for a nap 💤")
                                            LoadingDots(Modifier.padding(bottom = 16.dp))
                                        }
                                        is Phase.Ready -> MascotMessage(Mood.Excited, "${p.name} is ready! ♡")
                                        is Phase.Failed -> {
                                            MascotMessage(Mood.Sad, "Couldn't open it", p.message)
                                            Spacer(Modifier.height(8.dp))
                                            CandyButton("Okay", sound = Sfx.Tap, onClick = ::finish)
                                            Spacer(Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // The swap runs in the app scope so it finishes even if this card goes away.
        lifecycleScope.launch {
            val name = if (slot == SwapScripts.ORIGINAL) {
                app.apps.find(pkg)?.label?.let { "original $it" } ?: "original"
            } else {
                app.clones.get(slot)?.name ?: "your clone"
            }
            phase = Phase.Swapping(name)
            val started = System.currentTimeMillis()
            val swap = runCatching { app.scope.async { app.clones.prepareLaunch(pkg, slot) }.await() }
            delay((700 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            swap.fold(
                onSuccess = { launchIntent ->
                    app.sounds.play(Sfx.Success)
                    phase = Phase.Ready(name)
                    burst++
                    delay(650)
                    try {
                        startActivity(launchIntent)
                        finish()
                    } catch (e: ActivityNotFoundException) {
                        phase = Phase.Failed(MainViewModel.explain(e))
                    }
                },
                onFailure = {
                    app.sounds.play(Sfx.Oops)
                    phase = Phase.Failed(MainViewModel.explain(it))
                },
            )
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_SLOT = "slot"

        fun intent(context: Context, packageName: String, slot: String): Intent =
            Intent(context, LaunchCloneActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_SLOT, slot)
    }
}
