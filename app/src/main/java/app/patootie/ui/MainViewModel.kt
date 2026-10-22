package app.patootie.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.patootie.AppMissingException
import app.patootie.LaunchCloneActivity
import app.patootie.PatootieApp
import app.patootie.apps.InstalledApp
import app.patootie.data.Clone
import app.patootie.engine.SwapError
import app.patootie.engine.SwapScripts
import app.patootie.sound.Sfx
import app.patootie.ui.components.Mood
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object PickApp : Screen
    data class Create(val app: InstalledApp) : Screen
}

enum class RootState { Checking, Granted, Missing }

/** What Patootie is saying in its speech bubble. */
data class Speech(val text: String, val mood: Mood)

sealed interface CreateState {
    data object Editing : CreateState
    data object Working : CreateState
    data class Done(val clone: Clone) : CreateState
    data class Failed(val message: String) : CreateState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PatootieApp
    private val repo = app.clones
    val sounds = app.sounds

    val clones = repo.clones.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val status = repo.status

    var root by mutableStateOf(RootState.Checking); private set
    var screen by mutableStateOf<Screen>(Screen.Home); private set
    var installed by mutableStateOf<List<InstalledApp>?>(null); private set
    var createState by mutableStateOf<CreateState>(CreateState.Editing); private set
    /** Non-null while a long operation runs; shown as a cute overlay. */
    var busy by mutableStateOf<String?>(null); private set
    var speech by mutableStateOf(greeting()); private set
    private var speechReset: Job? = null

    init {
        checkRoot()
    }

    fun checkRoot() {
        root = RootState.Checking
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val ok = repo.hasRoot()
            // Let the "looking for root magic" moment breathe instead of flashing.
            delay((900 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            root = if (ok) RootState.Granted else RootState.Missing
            if (ok) refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { repo.refreshStatus() } }
    }

    fun go(to: Screen) {
        if (to is Screen.PickApp && installed == null) {
            viewModelScope.launch { installed = app.apps.list() }
        }
        if (to is Screen.Create) createState = CreateState.Editing
        screen = to
    }

    fun back() {
        screen = when (screen) {
            is Screen.Create -> Screen.PickApp
            else -> Screen.Home
        }
    }

    suspend fun defaultName(app: InstalledApp) = repo.nextDefaultName(app)

    suspend fun iconFor(packageName: String, tint: Int?, sticker: String?) = repo.icon(packageName, tint, sticker)

    suspend fun appIcon(packageName: String) = app.apps.icon(packageName)

    fun create(target: InstalledApp, name: String, tint: Int?, sticker: String?) {
        createState = CreateState.Working
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { repo.create(target, name, tint, sticker) }
            delay((1400 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            createState = result.fold(
                onSuccess = { sounds.play(Sfx.Success); CreateState.Done(it) },
                onFailure = { sounds.play(Sfx.Oops); CreateState.Failed(explain(it)) },
            )
        }
    }

    fun retryCreate() {
        createState = CreateState.Editing
    }

    fun launch(clone: Clone) = launch(clone.packageName, clone.id)

    fun launchOriginal(packageName: String) = launch(packageName, SwapScripts.ORIGINAL)

    private fun launch(packageName: String, slot: String) {
        app.startActivity(
            LaunchCloneActivity.intent(app, packageName, slot).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun rename(clone: Clone, name: String) {
        viewModelScope.launch {
            repo.rename(clone.id, name)
            say("Ta-da! It's “${name.trim()}” now", Mood.Excited, Sfx.Sparkle)
        }
    }

    fun updateFromOriginal(clone: Clone) = work("Syncing with the original…") {
        val updated = repo.updateFromOriginal(clone.id)
        say("${updated.name} is all synced up (v${updated.appVersion}) ✨", Mood.Excited, Sfx.Success)
    }

    fun delete(clone: Clone) = work("Saying bye-bye to ${clone.name}…") {
        repo.delete(clone.id)
        say("${clone.name} is gone. I'll miss it 🥲", Mood.Sad, Sfx.Pop)
    }

    fun pin(clone: Clone) {
        viewModelScope.launch {
            if (repo.pinShortcut(clone)) {
                say("Your launcher will ask to add it to your home screen 🏡", Mood.Happy, Sfx.Sparkle)
            } else {
                say("Your launcher doesn't let apps add icons 😿", Mood.Sad, Sfx.Oops)
            }
        }
    }

    /** Finishes a swap that was cut off (phone died, app killed) and re-fits the data. */
    fun finishInterrupted(packageName: String) = work("Patching things up…") {
        val target = repo.repair(packageName)
        say("All fixed up! ${target.label} is cozy again 🩹", Mood.Excited, Sfx.Success)
    }

    fun toggleSound() {
        sounds.enabled = !sounds.enabled
        if (sounds.enabled) sounds.play(Sfx.Sparkle)
        say(if (sounds.enabled) "Sounds on ♪" else "Shh… sounds off", Mood.Happy)
    }

    private fun work(message: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = message
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                say(explain(e), Mood.Sad, Sfx.Oops)
            } finally {
                busy = null
            }
        }
    }

    private fun say(text: String, mood: Mood, sfx: Sfx? = null) {
        sfx?.let(sounds::play)
        speech = Speech(text, mood)
        speechReset?.cancel()
        speechReset = viewModelScope.launch {
            delay(4500)
            speech = greeting()
        }
    }

    private fun greeting() = Speech(GREETINGS.random(), Mood.Happy)

    companion object {
        private val GREETINGS = listOf(
            "Hiii! Which clone are we playing with today? ♡",
            "Every clone gets its very own login~",
            "Tap a clone and I'll swap it in for you ✨",
            "Clones take turns, so they never get mixed up 💤",
        )

        fun explain(e: Throwable): String = when (e) {
            is AppMissingException -> "I can't find that app anymore. Reinstall it, then try again 🥺"
            is SwapError -> when (e.kind) {
                SwapError.Kind.NO_ROOT -> "Root said no 😿 Check that Patootie still has root access."
                SwapError.Kind.APP_DATA_MISSING -> "That app has no data folder yet. Open it once, then try again!"
                SwapError.Kind.CORRUPT_JOURNAL -> "A swap note got scrambled. Your data is safe in /data/adb/patootie; open “?” for help."
                SwapError.Kind.SLOT_BUSY -> "That clone is busy right now, try again in a sec."
                SwapError.Kind.SWAP_FAILED -> "The swap hiccuped: ${e.message}. Nothing was deleted."
            }
            else -> "Oopsie: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
