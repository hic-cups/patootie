package app.patootie.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A clone is a name and a look for one parked copy of an app's data. [id] doubles
 * as the engine's slot id, so it must stay short, lowercase and dash-only.
 */
@Entity(tableName = "clones")
data class Clone(
    @PrimaryKey val id: String,
    val packageName: String,
    val name: String,
    /** ARGB tint laid over the app icon, or null for the plain icon. */
    val tint: Int?,
    /** An emoji stuck to the icon's corner, or null for none. */
    val sticker: String?,
    /** Label and version of the original app, refreshed by "update from original". */
    val appLabel: String,
    val appVersion: String,
    val createdAt: Long,
    val lastLaunchedAt: Long? = null,
)

@Dao
interface CloneDao {
    @Query("SELECT * FROM clones ORDER BY appLabel COLLATE NOCASE, createdAt")
    fun observeAll(): Flow<List<Clone>>

    @Query("SELECT * FROM clones WHERE id = :id")
    suspend fun get(id: String): Clone?

    @Query("SELECT COUNT(*) FROM clones WHERE packageName = :packageName")
    suspend fun countFor(packageName: String): Int

    @Insert
    suspend fun insert(clone: Clone)

    @Update
    suspend fun update(clone: Clone)

    @Query("DELETE FROM clones WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [Clone::class], version = 1, exportSchema = true)
abstract class PatootieDatabase : RoomDatabase() {
    abstract fun clones(): CloneDao
}
