package dev.ramim.phonestatus.data

import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One long-lived `su` shell, so reading sensors every second does not
 * spawn a new root process each time.
 */
object RootShell {
    private const val MARK = "__PS_END__"
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var process: Process? = null
    @Volatile private var stdin: OutputStreamWriter? = null
    @Volatile private var stdout: BufferedReader? = null

    /** Runs [cmd] as root. Returns its output, or null on timeout, denied root or missing su. */
    fun exec(cmd: String, timeoutMs: Long): String? {
        val future = executor.submit(
            Callable<String?> {
                if (!ensureStarted()) return@Callable null
                val w = stdin ?: return@Callable null
                val r = stdout ?: return@Callable null
                w.write(cmd + "\necho " + MARK + "\n")
                w.flush()
                val sb = StringBuilder()
                while (true) {
                    val line = r.readLine() ?: return@Callable null
                    if (line == MARK) break
                    sb.append(line).append('\n')
                }
                sb.toString()
            },
        )
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            reset()
            null
        }
    }

    private fun ensureStarted(): Boolean {
        if (process?.isAlive == true && stdin != null && stdout != null) return true
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            process = p
            stdin = OutputStreamWriter(p.outputStream)
            stdout = p.inputStream.bufferedReader()
            true
        } catch (_: Exception) {
            false // su binary not found
        }
    }

    fun reset() {
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        stdin = null
        stdout = null
    }
}
