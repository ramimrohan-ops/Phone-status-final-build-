package dev.ramim.phonestatus.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Display
import java.io.File
import kotlin.math.abs

/** Reads everything that does NOT need root. All calls are cheap and safe to poll. */
object StatsReader {

    /** Some battery properties need a permission normal apps don't get; treat that as "not available". */
    private fun safeInt(bm: BatteryManager, id: Int): Int? = try {
        bm.getIntProperty(id)
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    fun readAll(ctx: Context): Stats = Stats(
        battery = battery(ctx),
        ram = ram(ctx),
        storage = storage(),
        display = display(ctx),
    )

    fun battery(ctx: Context): BatteryInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo()

        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0

        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "On battery"
        }

        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = safeInt(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        // Most devices report microamps, a few report milliamps.
        val currentMa = if (raw == Int.MIN_VALUE) null else {
            val v = abs(raw)
            if (v >= 20_000) v / 1000 else v
        }

        // No-root capacity: the phone's measured value if readable, else remaining charge / level.
        val counterUah = try {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (_: Exception) {
            0L
        }
        val capacityMah = RealCapacity.fullMah() ?: CapacityEstimator.update(ctx, counterUah, pct)

        // Android 14+ extras; older versions simply report nothing.
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it > 0 }
        } else null

        return BatteryInfo(
            level = pct,
            charging = charging,
            plug = plug,
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageV = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f,
            currentMa = currentMa,
            health = health,
            technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty(),
            capacityMah = capacityMah,
            designMah = DesignCapacity.read(ctx),
            cycleCount = cycles,
        )
    }

    fun ram(ctx: Context): RamInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        var swapTotal = 0L
        var swapFree = 0L
        try {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("SwapTotal:") -> swapTotal = kbToBytes(line)
                    line.startsWith("SwapFree:") -> swapFree = kbToBytes(line)
                }
            }
        } catch (_: Exception) {
            // /proc/meminfo unreadable: just hide swap
        }

        return RamInfo(
            totalBytes = mi.totalMem,
            usedBytes = (mi.totalMem - mi.availMem).coerceAtLeast(0L),
            swapTotalBytes = swapTotal,
            swapUsedBytes = (swapTotal - swapFree).coerceAtLeast(0L),
        )
    }

    fun storage(): StorageInfo {
        val fs = StatFs(Environment.getDataDirectory().path)
        val total = fs.blockCountLong * fs.blockSizeLong
        val free = fs.availableBlocksLong * fs.blockSizeLong
        return StorageInfo(totalBytes = total, usedBytes = (total - free).coerceAtLeast(0L))
    }

    fun display(ctx: Context): DisplayInfo {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return DisplayInfo()
        val mode = d.mode
        return DisplayInfo(
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            dpi = ctx.resources.displayMetrics.densityDpi,
        )
    }

    private fun kbToBytes(line: String): Long =
        (line.filter { it.isDigit() }.toLongOrNull() ?: 0L) * 1024L
}
