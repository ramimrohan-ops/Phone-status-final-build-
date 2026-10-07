package dev.ramim.phonestatus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import dev.ramim.phonestatus.MainActivity
import dev.ramim.phonestatus.R
import dev.ramim.phonestatus.data.BatteryInfo
import dev.ramim.phonestatus.data.Stats
import dev.ramim.phonestatus.data.ThermalInfo
import dev.ramim.phonestatus.ui.f1
import dev.ramim.phonestatus.ui.fmtFreq
import dev.ramim.phonestatus.ui.fmtMah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Builds the widget's RemoteViews and pushes them straight to the launcher. */
object WidgetRenderer {

    // LED thresholds
    private const val CHIP_WARM_C = 60f
    private const val CHIP_HOT_C = 75f
    private const val BATT_WARM_C = 38f
    private const val BATT_HOT_C = 42f
    private const val DRAIN_BUSY_MA = 1500
    private const val DRAIN_HIGH_MA = 2500

    private enum class Kind { SMALL, WIDE, TALL }

    fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, StatusWidgetProvider::class.java))

    fun renderAll(ctx: Context, stats: Stats, thermal: ThermalInfo) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        for (id in widgetIds(ctx)) {
            mgr.updateAppWidget(id, build(ctx, mgr.getAppWidgetOptions(id), stats, thermal, time))
        }
    }

    private fun build(
        ctx: Context,
        options: Bundle,
        s: Stats,
        t: ThermalInfo,
        time: String,
    ): RemoteViews {
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val kind = when {
            minW in 1..199 -> Kind.SMALL
            minH >= 150 -> Kind.TALL
            else -> Kind.WIDE
        }
        val layout = when (kind) {
            Kind.SMALL -> R.layout.widget_small
            Kind.WIDE -> R.layout.widget_wide
            Kind.TALL -> R.layout.widget_tall
        }

        val v = RemoteViews(ctx.packageName, layout)
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        bindBattery(v, s.battery)

        val cpuText = t.cpuC?.let { "${it.roundToInt()}°C" } ?: "—"
        val ramPct = (s.ram.fraction * 100).roundToInt()
        val stoPct = (s.storage.fraction * 100).roundToInt()
        val hz = s.display.currentHz.roundToInt()

        if (kind == Kind.SMALL) {
            v.setTextViewText(R.id.w_line1, "RAM $ramPct% · SSD $stoPct%")
            v.setTextViewText(R.id.w_line2, "CPU $cpuText · $hz Hz")
        } else {
            v.setTextViewText(R.id.w_batt_temp, "${f1(s.battery.tempC)} °C")
            v.setTextViewText(R.id.w_updated, "Live $time")
            v.setTextViewText(R.id.w_ram_val, "$ramPct%")
            v.setProgressBar(R.id.w_ram_bar, 100, ramPct, false)
            v.setTextViewText(R.id.w_sto_val, "$stoPct%")
            v.setProgressBar(R.id.w_sto_bar, 100, stoPct, false)
            v.setTextViewText(R.id.w_hz_cpu, "$hz Hz · CPU $cpuText")

            val cpuMhz = t.freq.cpuPeakMhz
            if (kind == Kind.WIDE) {
                v.setTextViewText(R.id.w_cpu_freq_line, "CPU ${fmtFreq(cpuMhz, 1)}")
                v.setTextViewText(R.id.w_gpu_freq_line, "GPU ${t.gpuC?.let { "${it.roundToInt()}°C" } ?: "—"}")
            }

            if (kind == Kind.TALL) {
                // Tall layout labels each cell itself, so the values carry no prefix.
                v.setTextViewText(R.id.w_hz_cpu, "$hz Hz")
                v.setTextViewText(R.id.w_cpu_line, tempClockText(t.cpuC, cpuMhz))
                v.setTextViewText(R.id.w_gpu_line, t.gpuC?.let { "${it.roundToInt()}°C" } ?: "—")

                // LED dots: green = fine, amber = busy / warm, red = high, grey = can't be read.
                v.setInt(R.id.w_led_batt, "setBackgroundResource", batteryLed(s.battery))
                v.setInt(R.id.w_led_cpu, "setBackgroundResource", tempLed(t.cpuC))
                v.setInt(R.id.w_led_gpu, "setBackgroundResource", tempLed(t.gpuC))

                val bat = s.battery
                v.setTextViewText(R.id.w_capacity, bat.capacityMah?.let { "~${fmtMah(it)}" } ?: "—")
                val health = bat.healthPct
                val cycles = bat.cycleCount
                v.setTextViewText(
                    R.id.w_cap_sub,
                    when {
                        health != null && cycles != null -> "$health% · $cycles cyc"
                        health != null -> "$health% health"
                        cycles != null -> "$cycles cycles"
                        else -> ""
                    },
                )
                v.setTextViewText(
                    R.id.w_current,
                    s.battery.currentMa?.let { (if (s.battery.charging) "↑" else "↓") + "$it mA" } ?: "—",
                )
                v.setTextViewText(
                    R.id.w_voltage,
                    String.format(Locale.US, "%.2f V", s.battery.voltageV),
                )
            }
        }
        return v
    }

    /** "43°C · 2.21 GHz". Parts the phone can't read are left out, "—" if nothing is readable. */
    private fun tempClockText(celsius: Float?, mhz: Int?): String {
        val parts = listOfNotNull(
            celsius?.let { "${it.roundToInt()}°C" },
            mhz?.let { fmtFreq(it) },
        )
        return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    }

    private fun tempLed(c: Float?): Int = when {
        c == null -> R.drawable.led_off
        c >= CHIP_HOT_C -> R.drawable.led_hot
        c >= CHIP_WARM_C -> R.drawable.led_warn
        else -> R.drawable.led_ok
    }

    /** Worst of: battery heat, low charge while unplugged, heavy drain while unplugged. */
    private fun batteryLed(b: BatteryInfo): Int {
        val drain = if (b.charging) 0 else (b.currentMa ?: 0)
        val low = !b.charging && b.level <= 15
        val lowish = !b.charging && b.level <= 30
        return when {
            b.tempC >= BATT_HOT_C || low || drain >= DRAIN_HIGH_MA -> R.drawable.led_hot
            b.tempC >= BATT_WARM_C || lowish || drain >= DRAIN_BUSY_MA -> R.drawable.led_warn
            else -> R.drawable.led_ok
        }
    }

    private fun bindBattery(v: RemoteViews, b: BatteryInfo) {
        val low = b.level <= 15 && !b.charging
        v.setTextViewText(R.id.w_battery_pct, "${b.level}%")
        v.setViewVisibility(R.id.w_charging, if (b.charging) View.VISIBLE else View.GONE)
        v.setProgressBar(R.id.w_battery_bar, 100, b.level, false)
        v.setProgressBar(R.id.w_battery_bar_low, 100, b.level, false)
        v.setViewVisibility(R.id.w_battery_bar, if (low) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.w_battery_bar_low, if (low) View.VISIBLE else View.GONE)
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
