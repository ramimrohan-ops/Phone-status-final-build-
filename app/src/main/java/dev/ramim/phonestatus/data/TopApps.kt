package dev.ramim.phonestatus.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.ramim.phonestatus.widget.WidgetRenderer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** One app and the memory (PSS, all its processes added up) it uses right now. */
data class AppMem(val label: String, val pkg: String, val mb: Int)

/** What the "Top apps" list shows. [apps] is empty until the first successful refresh; [note] says why. */
data class TopAppsState(
    val apps: List<AppMem> = emptyList(),
    val at: Long = 0L,
    val busy: Boolean = false,
    val note: String = "Tap Refresh",
    val source: String = "",
    val needShizukuPermission: Boolean = false,
)

/**
 * The memory report is read once per tap on Refresh, never on a timer.
 *  1. Direct: `dumpsys meminfo`, works when the DUMP permission was granted once from a shell (pm grant).
 *  2. Shizuku: the same command run through Shizuku, when it is installed, running and allowed.
 */
object TopApps {

    const val ACTION_REFRESH = "dev.ramim.phonestatus.REFRESH_APPS"

    private val _state = MutableStateFlow(TopAppsState())
    val state: StateFlow<TopAppsState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)

    /** Reads the report and redraws the widget. A second call while one is running is ignored. */
    suspend fun refresh(ctx: Context) {
        if (!running.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        try {
            _state.value = _state.value.copy(busy = true)
            redraw(app)
            val result = withContext(Dispatchers.IO) { read(app) }
            val old = _state.value
            _state.value = if (result.apps.isEmpty() && old.apps.isNotEmpty()) {
                // Keep the last good list (its age keeps counting) and remember why this try failed.
                old.copy(busy = false, note = result.note, source = result.source, needShizukuPermission = result.needShizukuPermission)
            } else {
                result
            }
        } catch (_: Throwable) {
            _state.value = _state.value.copy(busy = false, note = "Could not read")
        } finally {
            running.set(false)
            withContext(NonCancellable) { redraw(app) }
        }
    }

    private suspend fun redraw(app: Context) = withContext(Dispatchers.IO) {
        try {
            WidgetRenderer.redrawNow(app)
        } catch (_: Throwable) {
        }
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    private suspend fun read(ctx: Context): TopAppsState {
        // 1. Direct, with the DUMP permission.
        val granted = ctx.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val text = runDirect()
            if (text != null) return parse(ctx, text, "DUMP permission")
        }

        // 2. Shizuku.
        return when (ShizukuAccess.status(ctx)) {
            ShizukuAccess.Status.READY -> {
                val text = ShizukuAccess.exec(arrayOf("dumpsys", "meminfo"))
                    ?.takeIf { it.contains(MARK) }
                    ?: ShizukuAccess.exec(arrayOf("dumpsys", "activity", "meminfo"))?.takeIf { it.contains(MARK) }
                if (text != null) parse(ctx, text, "Shizuku")
                else TopAppsState(note = "Could not read", source = "Shizuku")
            }
            ShizukuAccess.Status.NO_PERMISSION -> {
                ShizukuAccess.requestPermission()
                TopAppsState(note = "Allow in Shizuku", needShizukuPermission = true)
            }
            ShizukuAccess.Status.NOT_RUNNING ->
                TopAppsState(note = if (granted) "Not available" else "Shizuku off")
            ShizukuAccess.Status.NOT_INSTALLED ->
                TopAppsState(note = "Not available")
        }
    }

    private fun runDirect(): String? {
        for (cmd in listOf(arrayOf("dumpsys", "meminfo"), arrayOf("dumpsys", "activity", "meminfo"))) {
            val text = runProcess(cmd)
            if (text != null && text.contains(MARK)) return text
        }
        return null
    }

    private fun runProcess(cmd: Array<String>): String? = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(6, TimeUnit.SECONDS)) p.destroy()
        out
    } catch (_: Throwable) {
        null
    }

    // ---- parsing -------------------------------------------------------------------------------------------------

    private const val MARK = "Total PSS by process:"
    private val line = Regex("""^\s*([\d,.]+)K:\s+(\S+)\s+\(pid\s+\d+""")

    private fun parse(ctx: Context, text: String, source: String): TopAppsState {
        val pm = ctx.packageManager
        val skip = HashSet<String>()
        skip.add(ctx.packageName)
        skip.add("system")
        skip.add("android")
        skip.add("com.android.systemui")
        try {
            val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            home?.activityInfo?.packageName?.let { skip.add(it) }
        } catch (_: Throwable) {
        }

        val totals = LinkedHashMap<String, Int>() // package -> KB
        val labels = HashMap<String, String>()
        var inside = false
        for (raw in text.lineSequence()) {
            if (!inside) {
                if (raw.contains(MARK)) inside = true
                continue
            }
            if (raw.isBlank()) break
            val m = line.find(raw) ?: continue
            val kb = m.groupValues[1].filter { it.isDigit() }.toLongOrNull() ?: continue
            val proc = m.groupValues[2]
            if (!proc.contains('.') || proc.startsWith("/")) continue // native services have no package name
            val pkg = resolvePackage(pm, proc.substringBefore(':')) ?: proc.substringBefore(':')
            if (pkg in skip) continue
            val info = appInfo(pm, pkg)
            if (info != null) {
                val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                if (system) continue // built-in system components, not "apps"
                if (pkg !in labels) labels[pkg] = try { pm.getApplicationLabel(info).toString() } catch (_: Throwable) { prettify(pkg) }
            } else if (pkg !in labels) {
                labels[pkg] = prettify(pkg)
            }
            totals[pkg] = (totals[pkg] ?: 0) + kb.toInt()
        }

        val top = totals.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { AppMem(labels[it.key] ?: prettify(it.key), it.key, it.value / 1024) }

        return if (top.isEmpty()) {
            TopAppsState(note = "Could not read", source = source)
        } else {
            TopAppsState(apps = top, at = System.currentTimeMillis(), note = "", source = source)
        }
    }

    private fun appInfo(pm: PackageManager, pkg: String): ApplicationInfo? = try {
        pm.getApplicationInfo(pkg, 0)
    } catch (_: Throwable) {
        null
    }

    /** "com.google.android.gms.persistent" is part of "com.google.android.gms": cut segments until a package matches. */
    private fun resolvePackage(pm: PackageManager, name: String): String? {
        var c = name
        while (c.count { it == '.' } >= 1) {
            if (appInfo(pm, c) != null) return c
            c = c.substringBeforeLast('.')
        }
        return null
    }

    private fun prettify(pkg: String): String =
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.US) }

    fun fmtMb(mb: Int): String =
        if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024f) else "$mb MB"

    /** "12s", "3m", "" when never read. */
    fun ageText(at: Long, now: Long = System.currentTimeMillis()): String {
        if (at == 0L) return ""
        val s = ((now - at) / 1000L).coerceAtLeast(0L)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            else -> "${s / 3600}h"
        }
    }
}

/** Shizuku as a helper that runs one shell command for us. Everything is wrapped: a missing Shizuku never crashes. */
object ShizukuAccess {

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    private const val MANAGER = "moe.shizuku.privileged.api"

    private fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(MANAGER, 0)
        true
    } catch (_: Throwable) {
        false
    }

    private fun alive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Looks at Shizuku, waiting a moment for it to connect when this process has just started. */
    suspend fun status(ctx: Context): Status {
        if (!installed(ctx)) return Status.NOT_INSTALLED
        var waited = 0
        while (!alive() && waited < 2_000) {
            delay(100)
            waited += 100
        }
        if (!alive()) return Status.NOT_RUNNING
        val ok = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        return if (ok) Status.READY else Status.NO_PERMISSION
    }

    fun requestPermission() {
        try {
            if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(4711)
        } catch (_: Throwable) {
        }
    }

    /** Runs [cmd] with shell rights through Shizuku and returns what it printed, or null. */
    fun exec(cmd: Array<String>): String? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, cmd, null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        try {
            p.waitFor()
        } catch (_: Throwable) {
        }
        out
    } catch (_: Throwable) {
        null
    }
}
