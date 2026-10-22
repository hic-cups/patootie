package app.patootie.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val isSuccess get() = exitCode == 0
}

/**
 * Runs one script per call in a fresh `su` process, fed through stdin so the
 * script never appears on a command line. Magisk, KernelSU and APatch all ship
 * a `su` that reads commands from stdin. Tests swap in plain `sh`.
 * `--mount-master` runs it in the global mount namespace: Patootie's own one
 * hides other apps' data dirs (Android 11+ app data isolation).
 */
class RootShell(private val command: List<String> = listOf("su", "--mount-master")) {

    suspend fun run(script: String): ShellResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(command).start()
        } catch (e: java.io.IOException) {
            return@withContext ShellResult(-1, "", e.message ?: "can't start ${command.first()}")
        }
        coroutineScope {
            val out = async(Dispatchers.IO) { process.inputStream.bufferedReader().readText() }
            val err = async(Dispatchers.IO) { process.errorStream.bufferedReader().readText() }
            try {
                process.outputStream.bufferedWriter().use {
                    it.write(script)
                    it.write("\nexit\n")
                }
            } catch (_: java.io.IOException) {
                // The shell died before reading everything (e.g. su denied); its exit code tells why.
            }
            val code = process.waitFor()
            ShellResult(code, out.await(), err.await())
        }
    }

    /** True when the shell really runs as uid 0 (the user granted root). */
    suspend fun isRoot(): Boolean = run("id -u").let { it.isSuccess && it.stdout.trim() == "0" }
}
