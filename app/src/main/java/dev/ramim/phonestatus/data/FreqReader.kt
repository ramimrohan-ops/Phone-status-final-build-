package dev.ramim.phonestatus.data

import java.io.File

/**
 * Current CPU (per core) and GPU clock speeds.
 * With root it uses the persistent shell, one round trip for everything.
 */
object FreqReader {

    @Volatile private var gpuPath: String? = null

    // Prints "c|<cpu dir>|<kHz>" for every core. Offline cores print an empty value.
    private const val CPU_CMD =
        "for c in /sys/devices/system/cpu/cpu[0-9]*; do f=; " +
            "read f 2>/dev/null < \$c/cpufreq/scaling_cur_freq; echo \"c|\$c|\$f\"; done"

    // Tries the usual GPU clock files (Adreno first), prints "g|<path>|<value>" for the first hit.
    private const val GPU_SCAN =
        "g=; for p in /sys/class/kgsl/kgsl-3d0/gpuclk /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq " +
            "/sys/class/devfreq/*gpu*/cur_freq /sys/class/devfreq/*kgsl*/cur_freq " +
            "/sys/kernel/gpu/gpu_clock /sys/class/misc/mali0/device/cur_freq; do " +
            "read g 2>/dev/null < \$p; " +
            "if [ -n \"\$g\" ]; then echo \"g|\$p|\$g\"; break; fi; done"

    private val cpuDirIndex = Regex("cpu(\\d+)$")

    fun read(viaRoot: Boolean): FreqInfo {
        if (!viaRoot) return readDirect()

        val cached = gpuPath
        val gpuCmd =
            if (cached != null) "g=; read g 2>/dev/null < $cached; echo \"g|$cached|\$g\""
            else GPU_SCAN

        val out = RootShell.exec(CPU_CMD + "\n" + gpuCmd, 3_000L) ?: return FreqInfo()

        val cores = sortedMapOf<Int, Int?>()
        var gpu: Int? = null
        var gpuSeen = false

        for (line in out.lineSequence()) {
            val p = line.split('|', limit = 3)
            if (p.size < 3) continue
            when (p[0]) {
                "c" -> {
                    val idx = cpuDirIndex.find(p[1].trim())?.groupValues?.get(1)?.toIntOrNull()
                    if (idx != null) cores[idx] = khzToMhz(p[2].trim().toLongOrNull())
                }
                "g" -> {
                    gpuSeen = true
                    val mhz = gpuToMhz(p[2].trim().toLongOrNull())
                    if (mhz != null) {
                        gpu = mhz
                        gpuPath = p[1].trim()
                    } else {
                        gpuPath = null // cached file stopped working, rescan next time
                    }
                }
            }
        }
        if (!gpuSeen && cached != null) gpuPath = null
        return FreqInfo(cores.values.toList(), gpu)
    }

    /** Without root most phones block this, but some allow it. No GPU clock in this mode. */
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
        if (cores.any { it != null }) FreqInfo(cores, null) else FreqInfo()
    } catch (_: Exception) {
        FreqInfo()
    }

    private fun khzToMhz(khz: Long?): Int? {
        if (khz == null || khz <= 0L) return null
        val mhz = (khz / 1000L).toInt()
        return if (mhz in 100..5000) mhz else null
    }

    /** Adreno reports Hz, some devfreq nodes kHz, some Mali nodes MHz: sort it out by size. */
    private fun gpuToMhz(raw: Long?): Int? {
        if (raw == null || raw <= 0L) return null
        val mhz = when {
            raw >= 5_000_000L -> (raw / 1_000_000L).toInt()
            raw >= 5_000L -> (raw / 1_000L).toInt()
            else -> raw.toInt()
        }
        return if (mhz in 50..2500) mhz else null
    }
}
