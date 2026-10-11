package dev.ramim.phonestatus.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.view.Display
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

        return BatteryInfo(
            level = pct,
            charging = charging,
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            currentMa = currentMa,
            capacityMah = capacityMah,
            designMah = DesignCapacity.read(ctx),
        )
    }

    fun ram(ctx: Context): RamInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        return RamInfo(
            totalBytes = mi.totalMem,
            usedBytes = (mi.totalMem - mi.availMem).coerceAtLeast(0L),
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
}
