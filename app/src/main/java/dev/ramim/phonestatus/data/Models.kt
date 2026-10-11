package dev.ramim.phonestatus.data

import kotlin.math.roundToInt

data class BatteryInfo(
    val level: Int = 0,
    val charging: Boolean = false,
    val tempC: Float = 0f,
    val currentMa: Int? = null,
    val capacityMah: Int? = null, // estimated (or measured, if readable) full capacity
    val designMah: Int? = null, // factory capacity, if the phone exposes it
) {
    /** Battery health in percent: capacity / design capacity, when both are known. */
    val healthPct: Int?
        get() {
            val cap = capacityMah
            val design = designMah
            if (cap == null || design == null || design <= 0) return null
            return (cap * 100f / design).roundToInt().coerceAtMost(100)
        }
}

data class RamInfo(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
) {
    val fraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class StorageInfo(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
) {
    val fraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class DisplayInfo(
    val width: Int = 0,
    val height: Int = 0,
    val dpi: Int = 0,
)

data class Stats(
    val battery: BatteryInfo = BatteryInfo(),
    val ram: RamInfo = RamInfo(),
    val storage: StorageInfo = StorageInfo(),
    val display: DisplayInfo = DisplayInfo(),
)

data class ThermalZone(val name: String, val celsius: Float)

enum class RootState { UNKNOWN, GRANTED, DENIED }

/** Current clock speeds in MHz. A null core is offline or unreadable. */
data class FreqInfo(
    val cpuCoresMhz: List<Int?> = emptyList(),
) {
    val cpuPeakMhz: Int? get() = cpuCoresMhz.filterNotNull().maxOrNull()
}

data class ThermalInfo(
    val cpuC: Float? = null,
    val gpuC: Float? = null,
    val sensorCount: Int = 0,
    val viaRoot: Boolean = false,
    val rootState: RootState = RootState.UNKNOWN,
    val freq: FreqInfo = FreqInfo(),
)
