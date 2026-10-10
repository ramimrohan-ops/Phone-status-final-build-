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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** One app and the memory (PSS, all its processes added up) it uses right now. */
data class AppMem(val label: String, val pkg: String, val mb: Int)

/** What the "Top apps" list shows. [apps] is empty until the first successful refresh; [note] says why. */
data class TopAppsState(
    val apps: List<AppMem> = emptyList(),
    val at: Long = 0L,
    val busy: Boolean = false,
    val note: String = "Tap Refresh",
    val source: String = "",
    /** Why the last read failed (permissions and the first words dumpsys printed), for the Debug tab. */
    val detail: String = "",
)

/**
 * The memory report is read once per tap on Refresh, never on a timer.
 * It runs `dumpsys meminfo` as this app. That needs two permissions, both granted once from a shell with pm grant:
 * DUMP and PACKAGE_USAGE_STATS.
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
                old.copy(busy = false, note = result.note, source = result.source, detail = result.detail)
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

    private fun has(ctx: Context, permission: String) =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun read(ctx: Context): TopAppsState {
        val dump = has(ctx, "android.permission.DUMP")
        val usage = has(ctx, "android.permission.PACKAGE_USAGE_STATS")
        val tries = StringBuilder()
        for (cmd in listOf(arrayOf("dumpsys", "meminfo"), arrayOf("dumpsys", "activity", "meminfo"))) {
            val text = runProcess(cmd)
            if (text != null && text.contains(MARK)) return parse(ctx, text, "DUMP permission")
            tries.append(cmd.joinToString(" ")).append(": ").append(firstWords(text)).append('\n')
        }
        val detail = "DUMP ${if (dump) "granted" else "missing"}, USAGE_STATS ${if (usage) "granted" else "missing"}\n" + tries.toString().trim()
        return TopAppsState(note = if (dump && usage) "Not available" else "Grant needed", detail = detail)
    }

    /** The start of what a command printed, on one line. */
    private fun firstWords(text: String?): String {
        if (text == null) return "could not run"
        val t = text.trim().replace(Regex("\\s+"), " ")
        return if (t.isEmpty()) "no output" else if (t.length > 140) t.substring(0, 140) + "…" else t
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
