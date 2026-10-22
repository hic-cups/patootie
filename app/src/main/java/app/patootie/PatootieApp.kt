package app.patootie

import android.app.Application
import androidx.room.Room
import app.patootie.apps.InstalledApps
import app.patootie.data.PatootieDatabase
import app.patootie.engine.CloneEngine
import app.patootie.sound.CuteSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PatootieApp : Application() {
    /** App-wide scope for work that must outlive a screen, like a swap in progress. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val apps by lazy { InstalledApps(this) }
    val sounds by lazy { CuteSounds(this) }
    val clones by lazy {
        val db = Room.databaseBuilder(this, PatootieDatabase::class.java, "patootie.db").build()
        CloneRepository(this, db.clones(), CloneEngine(), apps)
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch(Dispatchers.IO) { sounds.load() }
    }
}
