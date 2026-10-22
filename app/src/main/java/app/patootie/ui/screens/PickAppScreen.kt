package app.patootie.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.patootie.apps.InstalledApp
import app.patootie.sound.Sfx
import app.patootie.ui.components.AsyncIcon
import app.patootie.ui.components.BubbleButton
import app.patootie.ui.components.CuteTextField
import app.patootie.ui.components.LoadingDots
import app.patootie.ui.components.MascotMessage
import app.patootie.ui.components.Mood
import app.patootie.ui.components.bouncyClick
import app.patootie.ui.components.popIn

@Composable
fun PickAppScreen(
    apps: List<InstalledApp>?,
    icon: suspend (String) -> Drawable?,
    onBack: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BubbleButton(
                "Back",
                MaterialTheme.colorScheme.primaryContainer,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Text("Pick an app to clone", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        CuteTextField(query, { query = it }, label = "🔍  Search apps")
        Spacer(Modifier.height(12.dp))

        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MascotMessage(Mood.Thinking, "Peeking at your apps…")
                    LoadingDots()
                }
            }
            return@Column
        }

        val shown = remember(apps, query) {
            val q = query.trim()
            if (q.isEmpty()) apps else apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
        }
        if (shown.isEmpty()) {
            MascotMessage(Mood.Sad, "No apps match “$query”", modifier = Modifier.fillMaxWidth())
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(shown, key = { _, a -> a.packageName }) { index, app ->
                Column(
                    Modifier
                        .popIn((index.coerceAtMost(16) * 25).toLong())
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .bouncyClick(sound = Sfx.Pop) { onPick(app) }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncIcon(app.packageName, 54.dp) { icon(app.packageName)?.toBitmap(144, 144) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
