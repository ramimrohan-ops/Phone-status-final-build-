package dev.ramim.phonestatus.data

import java.io.File

/**
 * Reads /sys/class/thermal. Normal apps are blocked from this by SELinux on most phones,
 * so we go through the persistent root shell first and fall back to a direct read.
 *
 * To stay cheap at 1 Hz: a full scan of every sensor runs every 10th read,
 * the reads in between only touch the CPU/GPU sensors.
 */
object ThermalReader {

    @Volatile private var rootState = RootState.UNKNOWN
    @Volatile private var lastDeniedAtMs = 0L

    private val zonesByDir = LinkedHashMap<String, ThermalZone>()
    private var fastDirs: List<String> = emptyList()
    private var ticksSinceFull = Int.MAX_VALUE

    // Shell builtins only (read/echo), so no process is spawned per sensor.
    private const val BODY =
        "t=; v=; read t 2>/dev/null < \$z/type; read v 2>/dev/null < \$z/temp; echo \"\$z|\$t|\$v\""
    private const val FULL_CMD = "for z in /sys/class/thermal/thermal_zone*; do $BODY; done"

    /** Call when the user taps "Retry root access". */
    @Synchronized
    fun resetRoot() {
        rootState = RootState.UNKNOWN
        lastDeniedAtMs = 0L
        ticksSinceFull = Int.MAX_VALUE
        RootShell.reset()
    }

    @Synchronized
    fun read(allowRoot: Boolean = true): ThermalInfo {
        var viaRoot = false
        val now = System.currentTimeMillis()
        val retryDue = now - lastDeniedAtMs > 30_000L

        if (allowRoot && (rootState != RootState.DENIED || retryDue)) {
            val full = ticksSinceFull >= 10 || fastDirs.isEmpty()
            val cmd = if (full) FULL_CMD
            else "for z in ${fastDirs.joinToString(" ")}; do $BODY; done"
            // First attempt waits longer so there is time to tap "Grant" in the root prompt.
            val timeout = if (rootState == RootState.GRANTED) 3_000L else 20_000L

            val out = RootShell.exec(cmd, timeout)
            val parsed = if (out == null) emptyList() else parse(out)

            if (parsed.isNotEmpty() || (!full && out != null)) {
                rootState = RootState.GRANTED
                viaRoot = true
                if (full) {
                    zonesByDir.clear()
                    ticksSinceFull = 0
                    fastDirs = parsed.filter { (_, z) -> isCpuOrGpu(z.name) }.map { it.first }
                } else {
                    ticksSinceFull++
                }
                parsed.forEach { (dir, z) -> zonesByDir[dir] = z }
            } else {
                rootState = RootState.DENIED
                lastDeniedAtMs = now
                ticksSinceFull = Int.MAX_VALUE
            }
        }

        val zones = if (viaRoot) zonesByDir.values.toList() else readDirect()
        return summarize(zones, viaRoot).copy(freq = FreqReader.read(viaRoot))
    }

    private fun isCpuOrGpu(name: String) =
        name.contains("cpu", true) || name.contains("gpu", true) || name.contains("cluster", true)

    private fun readDirect(): List<ThermalZone> = try {
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            .orEmpty()
            .mapNotNull { dir ->
                try {
                    val type = File(dir, "type").readText().trim()
                    val raw = File(dir, "temp").readText().trim()
                    toZone(type, raw)
                } catch (_: Exception) {
                    null
                }
            }
    } catch (_: Exception) {
        emptyList()
    }

    /** Lines look like "/sys/class/thermal/thermal_zone5|cpu-0-0-usr|48200". */
    private fun parse(out: String): List<Pair<String, ThermalZone>> =
        out.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val zone = toZone(parts[1].trim(), parts[2].trim()) ?: return@mapNotNull null
                parts[0].trim() to zone
            }
            .toList()

    private fun toZone(type: String, raw: String): ThermalZone? {
        val v = raw.toFloatOrNull() ?: return null
        val c = if (kotlin.math.abs(v) >= 1000f) v / 1000f else v
        if (c !in 0f..150f) return null // drop invalid / disabled sensors
        return ThermalZone(type.ifBlank { "zone" }, c)
    }

    private fun summarize(zones: List<ThermalZone>, viaRoot: Boolean): ThermalInfo {
        val cpu = zones.filter { it.name.contains("cpu", true) || it.name.contains("cluster", true) }
            .maxOfOrNull { it.celsius }
        val gpu = zones.filter { it.name.contains("gpu", true) }.maxOfOrNull { it.celsius }
        return ThermalInfo(
            cpuC = cpu,
            gpuC = gpu,
            hottest = zones.maxByOrNull { it.celsius },
            sensorCount = zones.size,
            viaRoot = viaRoot,
            rootState = rootState,
        )
    }
}
