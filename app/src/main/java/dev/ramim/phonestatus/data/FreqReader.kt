package dev.ramim.phonestatus.data

import java.io.File

/**
 * Current CPU clock speed per core.
 * With root it uses the persistent shell, one round trip for everything.
 */
object FreqReader {

    // Prints "c|<cpu dir>|<kHz>" for every core. Offline cores print an empty value.
    private const val CPU_CMD =
        "for c in /sys/devices/system/cpu/cpu[0-9]*; do f=; " +
            "read f 2>/dev/null < \$c/cpufreq/scaling_cur_freq; echo \"c|\$c|\$f\"; done"

    private val cpuDirIndex = Regex("cpu(\\d+)$")

    fun read(viaRoot: Boolean): FreqInfo {
        if (!viaRoot) return readDirect()

        val out = RootShell.exec(CPU_CMD, 3_000L) ?: return FreqInfo()

        val cores = sortedMapOf<Int, Int?>()
        for (line in out.lineSequence()) {
            val p = line.split('|', limit = 3)
            if (p.size < 3 || p[0] != "c") continue
            val idx = cpuDirIndex.find(p[1].trim())?.groupValues?.get(1)?.toIntOrNull()
            if (idx != null) cores[idx] = khzToMhz(p[2].trim().toLongOrNull())
        }
        return FreqInfo(cores.values.toList())
    }

    /** Without root most phones block this, but some allow it. */
    private fun readDirect(): FreqInfo = try {
        val dirs = File("/sys/devices/system/cpu").listFiles()
            .orEmpty()
            .filter { cpuDirIndex.containsMatchIn(it.name) }
            .sortedBy { cpuDirIndex.find(it.name)!!.groupValues[1].toInt() }
        val cores = dirs.map { dir ->
            try {
                khzToMhz(File(dir, "cpufreq/scaling_cur_freq").readText().trim().toLongOrNull())
            } catch (_: Exception) {
                null
            }
        }
        if (cores.any { it != null }) FreqInfo(cores) else FreqInfo()
    } catch (_: Exception) {
        FreqInfo()
    }

    private fun khzToMhz(khz: Long?): Int? {
        if (khz == null || khz <= 0L) return null
        val mhz = (khz / 1000L).toInt()
        return if (mhz in 100..5000) mhz else null
    }
}
