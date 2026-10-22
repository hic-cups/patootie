package app.patootie.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.patootie.data.Clone
import app.patootie.sound.Sfx
import app.patootie.ui.components.CandyButton
import app.patootie.ui.components.CuteDialog
import app.patootie.ui.components.CuteTextField
import app.patootie.ui.components.DialogTitle
import app.patootie.ui.components.Mood
import app.patootie.ui.components.bouncyClick

@Composable
fun RenameDialog(clone: Clone, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable(clone.id) { mutableStateOf(clone.name) }
    CuteDialog(Mood.Happy, onDismiss) {
        DialogTitle("New name for ${clone.name}?")
        CuteTextField(name, { name = it }, label = "Clone name")
        CandyButton("Save ✨", enabled = name.isNotBlank(), sound = Sfx.Sparkle, onClick = { onRename(name) })
        TextAction("Never mind", onDismiss)
    }
}

@Composable
fun DeleteDialog(clone: Clone, onDismiss: () -> Unit, onDelete: () -> Unit) {
    CuteDialog(Mood.Sad, onDismiss) {
        DialogTitle("Delete ${clone.name}?")
        Body("Its login and everything saved in it go away forever. The original ${clone.appLabel} and your other clones stay safe.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Keep it 💕", onDismiss)
            CandyButton(
                "Delete",
                colors = listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.tertiary),
                sound = Sfx.Tap,
                onClick = onDelete,
            )
        }
    }
}

@Composable
fun UpdateDialog(clone: Clone, onDismiss: () -> Unit, onUpdate: () -> Unit) {
    CuteDialog(Mood.Thinking, onDismiss) {
        DialogTitle("Update from original?")
        Body(
            "Clones always run your installed ${clone.appLabel}, so app updates reach them automatically. " +
                "This refreshes the clone's name tag, version and icon from the original, and re-fits its data " +
                "if you reinstalled the app. Logins and data are kept.",
        )
        CandyButton("Update 🔄", sound = Sfx.Tap, onClick = onUpdate)
        TextAction("Not now", onDismiss)
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    CuteDialog(Mood.Happy, onDismiss) {
        DialogTitle("How Patootie works")
        Column(
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Body("🐰  Every clone runs your real, installed app: same package, same signature, same updates. Nothing is renamed or virtualized.")
            Body("💤  Clones take turns. When you open one, I stop the app, tuck the current data away and move that clone's data in. It's a rename, so it's instant even for big apps.")
            Body("🔔  Only the clone you opened last is “live”, so only it gets notifications and background syncs.")
            Body("🏠  “Original” is the data the app had before you made clones. Tap it to go back any time.")
            Body("🔐  Tucked-away data lives in /data/adb/patootie, which only root can read. It stays there even if you uninstall Patootie.")
            Body("⚠️  A few apps bind logins to hardware keys or device checks. Those may ask you to log in again after a swap.")
        }
        CandyButton("Got it ♡", sound = Sfx.Sparkle, onClick = onDismiss)
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.bouncyClick(onClick = onClick).padding(10.dp),
    )
}
