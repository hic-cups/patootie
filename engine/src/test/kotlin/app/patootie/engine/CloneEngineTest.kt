package app.patootie.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Runs the real generated scripts with `sh` against a fake /data tree. Android-only
 * commands (am, chown, restorecon) are stubbed; everything else is the real thing.
 */
class CloneEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val pkg = "com.example.chat"
    private val app = TargetApp(pkg, 10123)
    private lateinit var root: File
    private lateinit var layout: DataLayout
    private lateinit var shell: RootShell
    private lateinit var engine: CloneEngine

    private val ce get() = File(root, "user/0/$pkg")
    private val de get() = File(root, "user_de/0/$pkg")
    private val ext get() = File(root, "media/0/Android/data/$pkg")
    private val store get() = File(root, "store/0/$pkg")

    @Before fun setUp() {
        root = tmp.newFolder("data")
        layout = DataLayout(
            storeRoot = "${root.path}/store",
            ceRoot = "${root.path}/user",
            deRoot = "${root.path}/user_de",
            mediaRoot = "${root.path}/media",
        )
        shell = RootShell(testShell())
        engine = CloneEngine(shell, layout)
        // A logged-in "original" install.
        write(ce, "shared_prefs/login.xml", "alice")
        write(ce, ".hidden", "dot")
        write(ce, "databases/msgs.db", "hi bob")
        File(ce, "cache").mkdirs()
        write(de, "files/de.txt", "de-alice")
        write(ext, "files/photo.jpg", "jpeg-alice")
    }

    @Test fun freshCloneStartsEmptyAndOriginalComesBackIntact() = runBlocking {
        engine.prepare(app)
        engine.activate(app, "c1")

        assertEquals(setOf("cache", "code_cache"), ce.list()!!.toSet())
        assertTrue(de.list()!!.isEmpty())
        assertTrue(ext.list()!!.isEmpty())
        assertEquals("c1", File(store, "ACTIVE").readText().trim())

        engine.activate(app, SwapScripts.ORIGINAL)

        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
        assertEquals("dot", read(ce, ".hidden"))
        assertEquals("hi bob", read(ce, "databases/msgs.db"))
        assertEquals("de-alice", read(de, "files/de.txt"))
        assertEquals("jpeg-alice", read(ext, "files/photo.jpg"))
        assertFalse(File(store, "slots/original").exists())
        assertFalse(File(store, "JOURNAL").exists())
    }

    @Test fun clonesKeepTheirOwnLogins() = runBlocking {
        engine.activate(app, "c1")
        write(ce, "shared_prefs/login.xml", "carol")
        engine.activate(app, "c2")
        write(ce, "shared_prefs/login.xml", "dave")

        engine.activate(app, "c1")
        assertEquals("carol", read(ce, "shared_prefs/login.xml"))
        engine.activate(app, SwapScripts.ORIGINAL)
        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
        engine.activate(app, "c2")
        assertEquals("dave", read(ce, "shared_prefs/login.xml"))

        val status = engine.status(0).getValue(pkg)
        assertEquals("c2", status.activeSlot)
        assertFalse(status.interrupted)
        assertEquals(setOf("original", "c1"), status.slotSizesKb.keys)
    }

    @Test fun activatingTheLiveSlotIsANoOp() = runBlocking {
        engine.activate(app, "c1")
        write(ce, "files/note.txt", "keep me")
        engine.activate(app, "c1")
        assertEquals("keep me", read(ce, "files/note.txt"))
    }

    @Test fun resumesASwapInterruptedWhileParking() = runBlocking {
        engine.prepare(app)
        // Crash halfway through parking the original: some files moved, some not.
        val parked = File(store, "slots/original/ce").apply { mkdirs() }
        Files.move(File(ce, "databases").toPath(), File(parked, "databases").toPath())
        File(store, "JOURNAL").writeText("original c1 save\n")
        assertTrue(engine.status(0).getValue(pkg).interrupted)

        engine.activate(app, "c1")
        assertEquals(setOf("cache", "code_cache"), ce.list()!!.toSet())

        engine.activate(app, SwapScripts.ORIGINAL)
        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
        assertEquals("hi bob", read(ce, "databases/msgs.db"))
    }

    @Test fun resumesASwapInterruptedWhileRestoring() = runBlocking {
        engine.activate(app, "c1")
        write(ce, "shared_prefs/login.xml", "carol")
        engine.activate(app, SwapScripts.ORIGINAL)
        // Crash halfway through bringing c1 back in, after the original was parked.
        val parkedOriginal = File(store, "slots/original/ce").apply { mkdirs() }
        ce.listFiles()!!.forEach { Files.move(it.toPath(), File(parkedOriginal, it.name).toPath()) }
        val c1 = File(store, "slots/c1/ce")
        File(c1, "shared_prefs").copyRecursively(File(ce, "shared_prefs"))
        File(c1, "shared_prefs").deleteRecursively()
        File(store, "JOURNAL").writeText("original c1 restore\n")

        engine.activate(app, "c2")

        engine.activate(app, "c1")
        assertEquals("carol", read(ce, "shared_prefs/login.xml"))
        engine.activate(app, SwapScripts.ORIGINAL)
        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
    }

    @Test fun deletingTheLiveCloneBringsTheOriginalBackAndCleansUp() = runBlocking {
        engine.activate(app, "c1")
        write(ce, "shared_prefs/login.xml", "carol")
        engine.delete(app, "c1")

        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
        assertFalse("store is removed once no clone data is left", store.exists())
        assertNull(engine.status(0)[pkg])
    }

    @Test fun deletingAParkedCloneKeepsTheOthers() = runBlocking {
        engine.activate(app, "c1")
        engine.activate(app, "c2")
        write(ce, "files/c2.txt", "two")
        engine.delete(app, "c1")
        assertFalse(File(store, "slots/c1").exists())
        assertEquals("two", read(ce, "files/c2.txt"))
        engine.activate(app, SwapScripts.ORIGINAL)
        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
    }

    @Test fun neverOverwritesExistingFiles() = runBlocking {
        engine.prepare(app)
        // Stale file already parked where the live copy is about to go.
        write(File(store, "slots/original/ce"), "shared_prefs/login.xml", "stale")
        engine.activate(app, "c1")
        val names = File(store, "slots/original/ce").list()!!.toSet()
        assertTrue(names.any { it.startsWith("shared_prefs.patootie-bak-") })
        assertTrue("shared_prefs" in names)
    }

    @Test fun leavesTheSystemLibSymlinkInPlace() = runBlocking {
        val target = tmp.newFolder("native")
        Files.createSymbolicLink(File(ce, "lib").toPath(), target.toPath())
        engine.activate(app, "c1")
        assertTrue(Files.isSymbolicLink(File(ce, "lib").toPath()))
        engine.activate(app, SwapScripts.ORIGINAL)
        assertTrue(Files.isSymbolicLink(File(ce, "lib").toPath()))
        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
    }

    @Test fun missingAppDataIsReportedNotCreated() = runBlocking {
        ce.deleteRecursively()
        val e = assertThrows(SwapError::class.java) { runBlocking { engine.activate(app, "c1") } }
        assertEquals(SwapError.Kind.APP_DATA_MISSING, e.kind)
    }

    @Test fun corruptJournalStopsTheSwap() = runBlocking {
        engine.prepare(app)
        File(store, "JOURNAL").writeText("garbage\n")
        val e = assertThrows(SwapError::class.java) { runBlocking { engine.activate(app, "c1") } }
        assertEquals(SwapError.Kind.CORRUPT_JOURNAL, e.kind)
        assertEquals("alice", read(ce, "shared_prefs/login.xml"))
    }

    @Test fun rejectsAnythingThatCouldEscapeTheScript() {
        assertThrows(IllegalArgumentException::class.java) {
            SwapScripts.activate(layout, 0, "com.x'; rm -rf /; '", 10123, "c1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SwapScripts.activate(layout, 0, pkg, 10123, "../../etc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SwapScripts.activate(layout, 0, pkg, 1000, "c1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SwapScripts.deleteSlot(layout, 0, pkg, 10123, SwapScripts.ORIGINAL)
        }
    }

    @Test fun parsesStatusOutput() {
        val parsed = CloneEngine.parseStatus(
            "STATUS a.b c1 ok\nSIZE a.b original 120\nSTATUS x.y original pending\nnoise\n",
        )
        assertEquals("c1", parsed.getValue("a.b").activeSlot)
        assertEquals(mapOf("original" to 120L), parsed.getValue("a.b").slotSizesKb)
        assertTrue(parsed.getValue("x.y").interrupted)
    }

    /**
     * `-Dpatootie.shell=mksh` runs the scripts under Android's /system/bin/sh, and
     * `busybox` runs them with busybox applets (closest to toybox) instead of GNU tools.
     */
    private fun testShell(): List<String> {
        val stubs = "am() { :; }; chown() { :; }; restorecon() { :; }; eval \"\$(cat)\""
        return when (val which = System.getProperty("patootie.shell", "sh")) {
            "busybox" -> {
                val bin = tmp.newFolder("bin")
                for (applet in listOf("sh", "mv", "mkdir", "rm", "cat", "ls", "du", "cut", "stat", "chmod", "id")) {
                    Files.createSymbolicLink(File(bin, applet).toPath(), File("/usr/bin/busybox").toPath())
                }
                listOf("${bin.path}/sh", "-c", "PATH=${bin.path}; $stubs")
            }
            else -> listOf(which, "-c", stubs)
        }
    }

    private fun write(dir: File, rel: String, text: String) =
        File(dir, rel).apply { parentFile.mkdirs() }.writeText(text)

    private fun read(dir: File, rel: String) = File(dir, rel).readText()
}
