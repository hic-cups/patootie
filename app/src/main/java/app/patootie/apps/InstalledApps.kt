package app.patootie.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import app.patootie.engine.TargetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val uid: Int,
)

/** The launcher apps on this device, minus Patootie itself. */
class InstalledApps(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager
    private val iconCache = HashMap<String, Drawable>()

    suspend fun list(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { find(it) }
            .sortedBy { it.label.lowercase() }
    }

    fun find(packageName: String): InstalledApp? = try {
        val info = pm.getPackageInfo(packageName, 0)
        val appInfo = info.applicationInfo ?: return null
        InstalledApp(
            packageName = packageName,
            label = appInfo.loadLabel(pm).toString(),
            versionName = info.versionName ?: "?",
            uid = appInfo.uid,
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    suspend fun icon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        synchronized(iconCache) { iconCache[packageName] }
            ?: try {
                pm.getApplicationIcon(packageName).also { synchronized(iconCache) { iconCache[packageName] = it } }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
    }

    fun forgetIcon(packageName: String) = synchronized(iconCache) { iconCache.remove(packageName) }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    companion object {
        val myUserId: Int get() = Process.myUid() / 100_000
    }
}

fun InstalledApp.toTarget() = TargetApp(packageName, uid)
