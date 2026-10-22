package app.patootie

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.patootie.apps.InstalledApp
import app.patootie.apps.InstalledApps
import app.patootie.apps.toTarget
import app.patootie.data.Clone
import app.patootie.data.CloneDao
import app.patootie.engine.CloneEngine
import app.patootie.engine.PackageStatus
import app.patootie.engine.SwapScripts
import app.patootie.icon.CloneIconRenderer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class AppMissingException(val packageName: String) : Exception("$packageName isn't installed anymore")

/** Everything the UI can do to clones goes through here: database, root engine and shortcuts in step. */
class CloneRepository(
    private val context: Context,
    private val dao: CloneDao,
    private val engine: CloneEngine,
    private val apps: InstalledApps,
) {
    val clones: Flow<List<Clone>> = dao.observeAll()

    private val _status = MutableStateFlow<Map<String, PackageStatus>>(emptyMap())
    /** Which slot is live per package, plus interrupted swaps. Refresh with [refreshStatus]. */
    val status: StateFlow<Map<String, PackageStatus>> = _status

    suspend fun hasRoot() = engine.hasRoot()

    suspend fun refreshStatus() {
        _status.value = engine.status(InstalledApps.myUserId)
    }

    suspend fun get(id: String) = dao.get(id)

    suspend fun nextDefaultName(app: InstalledApp) = "${app.label} ${dao.countFor(app.packageName) + 2}"

    suspend fun create(app: InstalledApp, name: String, tint: Int?, sticker: String?): Clone {
        engine.prepare(app.toTarget())
        val clone = Clone(
            id = "c" + UUID.randomUUID().toString().replace("-", "").take(11),
            packageName = app.packageName,
            name = name.trim().ifEmpty { app.label },
            tint = tint,
            sticker = sticker,
            appLabel = app.label,
            appVersion = app.versionName,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(clone)
        refreshStatus()
        return clone
    }

    /**
     * Swaps [cloneId]'s data in (or the original's, for [SwapScripts.ORIGINAL]) and
     * returns the intent that opens the app.
     */
    suspend fun prepareLaunch(packageName: String, cloneId: String): Intent {
        require(cloneId == SwapScripts.ORIGINAL || dao.get(cloneId)?.packageName == packageName) {
            "no clone $cloneId for $packageName"
        }
        val app = apps.find(packageName) ?: throw AppMissingException(packageName)
        val intent = apps.launchIntent(packageName) ?: throw AppMissingException(packageName)
        engine.activate(app.toTarget(), cloneId)
        if (cloneId != SwapScripts.ORIGINAL) {
            dao.get(cloneId)?.let { clone ->
                val launched = clone.copy(lastLaunchedAt = System.currentTimeMillis())
                dao.update(launched)
                runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut(launched)) }
            }
        }
        refreshStatus()
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    suspend fun rename(id: String, name: String) {
        val clone = dao.get(id) ?: return
        val renamed = clone.copy(name = name.trim().ifEmpty { clone.name })
        dao.update(renamed)
        refreshShortcut(renamed)
    }

    /**
     * Clones always run the installed original, so they pick up its updates on their
     * own. This refreshes what the clone remembers about the original (label, version,
     * icon) and re-fits clone data to the app's current uid, which changes when the
     * original was uninstalled and reinstalled.
     */
    suspend fun updateFromOriginal(id: String): Clone {
        val clone = dao.get(id) ?: throw NoSuchElementException(id)
        val app = apps.find(clone.packageName) ?: throw AppMissingException(clone.packageName)
        engine.repair(app.toTarget())
        apps.forgetIcon(app.packageName)
        val updated = clone.copy(appLabel = app.label, appVersion = app.versionName)
        dao.update(updated)
        refreshShortcut(updated)
        refreshStatus()
        return updated
    }

    /** Finishes an interrupted swap for [packageName] and re-fits its data. */
    suspend fun repair(packageName: String): InstalledApp {
        val app = apps.find(packageName) ?: throw AppMissingException(packageName)
        engine.repair(app.toTarget())
        refreshStatus()
        return app
    }

    suspend fun delete(id: String) {
        val clone = dao.get(id) ?: return
        val app = apps.find(clone.packageName)
        // If the app itself is gone its data went with it; only our parked copy is left,
        // and the engine needs the app's uid to touch it. Keep the row so nothing is lost
        // silently: the user can reinstall the app and delete again.
        if (app == null) throw AppMissingException(clone.packageName)
        engine.delete(app.toTarget(), id)
        dao.delete(id)
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(id))
            ShortcutManagerCompat.disableShortcuts(context, listOf(id), "This clone was deleted")
        }
        refreshStatus()
    }

    suspend fun icon(clone: Clone): Bitmap? = icon(clone.packageName, clone.tint, clone.sticker)

    suspend fun icon(packageName: String, tint: Int?, sticker: String?): Bitmap? =
        apps.icon(packageName)?.let { CloneIconRenderer.render(it, tint, sticker) }

    /** Asks the launcher to pin a home-screen icon for the clone. False if it can't. */
    suspend fun pinShortcut(clone: Clone): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context) &&
            ShortcutManagerCompat.requestPinShortcut(context, shortcut(clone), null)

    private suspend fun refreshShortcut(clone: Clone) {
        runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut(clone))) }
    }

    private suspend fun shortcut(clone: Clone): ShortcutInfoCompat {
        val builder = ShortcutInfoCompat.Builder(context, clone.id)
            .setShortLabel(clone.name)
            .setLongLabel("${clone.name} · ${clone.appLabel}")
            .setIntent(LaunchCloneActivity.intent(context, clone.packageName, clone.id))
        icon(clone)?.let { builder.setIcon(IconCompat.createWithBitmap(it)) }
        return builder.build()
    }
}
