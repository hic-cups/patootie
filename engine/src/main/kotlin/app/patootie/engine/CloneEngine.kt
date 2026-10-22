package app.patootie.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The installed app a clone belongs to: package name plus its current uid. */
data class TargetApp(val packageName: String, val uid: Int) {
    val userId get() = uid / 100_000
}

data class PackageStatus(
    val packageName: String,
    val activeSlot: String,
    val interrupted: Boolean,
    /** Parked slot id → size on disk in KiB. The live slot isn't listed. */
    val slotSizesKb: Map<String, Long>,
)

class SwapError(val code: Int, message: String) : Exception(message) {
    val kind: Kind
        get() = when (code) {
            SwapScripts.EXIT_NO_APP_DATA -> Kind.APP_DATA_MISSING
            SwapScripts.EXIT_CORRUPT_JOURNAL -> Kind.CORRUPT_JOURNAL
            SwapScripts.EXIT_SLOT_IS_LIVE, SwapScripts.EXIT_SLOT_IN_FLIGHT -> Kind.SLOT_BUSY
            -1, 1 -> Kind.NO_ROOT // su missing or denied; the scripts themselves exit with 10+
            else -> Kind.SWAP_FAILED
        }

    enum class Kind { NO_ROOT, APP_DATA_MISSING, CORRUPT_JOURNAL, SLOT_BUSY, SWAP_FAILED }
}

/**
 * Clones by swapping data, not by duplicating apps: every clone of an app runs
 * the one installed copy (same package, same signature, same updates), and only
 * one clone per app is "live" in the app's data dir at a time. The others are
 * parked in the root-only store. Launching a clone parks the live one and moves
 * the chosen one in.
 */
class CloneEngine(
    private val shell: RootShell = RootShell(),
    private val layout: DataLayout = DataLayout(),
) {
    private val lock = Mutex()

    suspend fun hasRoot(): Boolean = shell.isRoot()

    /** Call before saving a new clone: checks the app's data dir and sets up its store. */
    suspend fun prepare(app: TargetApp) {
        exec(SwapScripts.prepare(layout, app.userId, app.packageName, app.uid))
    }

    /** Makes [slotId] the live data of [app]. The app is force-stopped first. */
    suspend fun activate(app: TargetApp, slotId: String) {
        exec(SwapScripts.activate(layout, app.userId, app.packageName, app.uid, slotId))
    }

    /** Deletes a clone's data forever, swapping the original back in first if it's live. */
    suspend fun delete(app: TargetApp, slotId: String) {
        lock.withLock {
            val live = statusUnlocked(app.userId)[app.packageName]
            if (live != null && (live.activeSlot == slotId || live.interrupted)) {
                execUnlocked(SwapScripts.activate(layout, app.userId, app.packageName, app.uid, SwapScripts.ORIGINAL))
            }
            execUnlocked(SwapScripts.deleteSlot(layout, app.userId, app.packageName, app.uid, slotId))
        }
    }

    /** Re-fits all clone data to the app's current uid, e.g. after a reinstall. */
    suspend fun repair(app: TargetApp) {
        exec(SwapScripts.repair(layout, app.userId, app.packageName, app.uid))
    }

    suspend fun status(userId: Int): Map<String, PackageStatus> = lock.withLock { statusUnlocked(userId) }

    private suspend fun statusUnlocked(userId: Int): Map<String, PackageStatus> {
        val out = execUnlocked(SwapScripts.status(layout, userId))
        return parseStatus(out)
    }

    private suspend fun exec(script: String): String = lock.withLock { execUnlocked(script) }

    private suspend fun execUnlocked(script: String): String {
        val result = shell.run(script)
        if (!result.isSuccess) {
            val detail = result.stderr.lineSequence().lastOrNull { it.startsWith("ERR ") }
                ?.substringAfter(' ')?.substringAfter(' ')
                ?: result.stderr.trim().ifEmpty { "exit ${result.exitCode}" }
            throw SwapError(result.exitCode, detail)
        }
        return result.stdout
    }

    companion object {
        fun parseStatus(out: String): Map<String, PackageStatus> {
            val active = LinkedHashMap<String, Pair<String, Boolean>>()
            val sizes = HashMap<String, MutableMap<String, Long>>()
            for (line in out.lineSequence()) {
                val parts = line.trim().split(' ')
                when {
                    parts.size == 4 && parts[0] == "STATUS" ->
                        active[parts[1]] = parts[2] to (parts[3] == "pending")
                    parts.size == 4 && parts[0] == "SIZE" ->
                        sizes.getOrPut(parts[1]) { HashMap() }[parts[2]] = parts[3].toLongOrNull() ?: 0L
                }
            }
            return active.mapValues { (pkg, state) ->
                PackageStatus(pkg, state.first, state.second, sizes[pkg].orEmpty())
            }
        }
    }
}
