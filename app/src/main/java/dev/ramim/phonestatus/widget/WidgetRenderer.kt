package dev.ramim.phonestatus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.ramim.phonestatus.R
import dev.ramim.phonestatus.data.BatteryInfo
import dev.ramim.phonestatus.data.Stats
import dev.ramim.phonestatus.data.StatsReader
import dev.ramim.phonestatus.data.ThermalInfo
import dev.ramim.phonestatus.data.TopApps
import dev.ramim.phonestatus.live.LiveStats
import dev.ramim.phonestatus.ui.f1
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds the widget's RemoteViews and pushes them straight to the launcher.
 * Only the Top apps bar (with its Refresh pill) is clickable; a tap anywhere else does nothing.
 */
object WidgetRenderer {

    // LED / ring thresholds
    private const val CHIP_WARM_C = 60f
    private const val CHIP_HOT_C = 75f
    private const val BATT_WARM_C = 38f
    private const val BATT_HOT_C = 42f
    private const val DRAIN_BUSY_MA = 1500
    private const val DRAIN_HIGH_MA = 2500

    private const val MINT = 0xFF3DF5A7.toInt()
    private const val AMBER = 0xFFFFB454.toInt()
    private const val RED = 0xFFFF5470.toInt()
    private const val GREY = 0xFF4A5270.toInt()
    private const val SOFT_WHITE = 0xFFE6ECFA.toInt()

    /** The CPU ring turns amber from this clock on: the top clock of this phone, 2.4 GHz (anything that rounds to 2.4 or more). */
    private const val CPU_TOP_MHZ = 2350

    private class AppRow(val row: Int, val rank: Int, val name: Int, val mb: Int)

    private val appRows = listOf(
        AppRow(R.id.w_app1_row, R.id.w_app1_rank, R.id.w_app1_name, R.id.w_app1_mb),
        AppRow(R.id.w_app2_row, R.id.w_app2_rank, R.id.w_app2_name, R.id.w_app2_mb),
        AppRow(R.id.w_app3_row, R.id.w_app3_rank, R.id.w_app3_name, R.id.w_app3_mb),
        AppRow(R.id.w_app4_row, R.id.w_app4_rank, R.id.w_app4_name, R.id.w_app4_mb),
    )

    fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, StatusWidgetProvider::class.java))

    fun renderAll(ctx: Context, stats: Stats, thermal: ThermalInfo) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = widgetIds(ctx)
        if (ids.isEmpty()) return
        val views = build(ctx, stats, thermal)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    /** Draws once with fresh numbers, outside the update loop (used by the Refresh button). */
    fun redrawNow(ctx: Context) {
        renderAll(ctx, StatsReader.readAll(ctx), LiveStats.thermal ?: ThermalInfo())
    }

    private fun build(ctx: Context, s: Stats, t: ThermalInfo): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_rect)
        val density = ctx.resources.displayMetrics.density
        val ring = (72 * density).roundToInt()
        val b = s.battery

        // Top apps bar (label + Refresh pill): the only thing on the widget that reacts to a tap.
        val ta = TopApps.state.value
        v.setOnClickPendingIntent(R.id.w_refresh, refreshIntent(ctx))
        v.setTextViewText(R.id.w_refresh_text, if (ta.busy) "Reading…" else "Refresh")

        // ---- Battery ring ----
        val battColor = batteryColor(b)
        v.setImageViewBitmap(R.id.w_batt_ring, Rings.arc(ring, b.level / 100f, battColor, 0.10f))
        v.setTextViewText(R.id.w_battery_pct, "${b.level}")
        v.setTextViewText(R.id.w_batt_temp, "${f1(b.tempC)}°")

        // ---- Current draw: a solid circle (colour follows the drain), dark text on it ----
        val ma = b.currentMa
        v.setImageViewBitmap(R.id.w_cur_ring, Rings.disc(ring, currentColor(b)))
        v.setTextViewText(R.id.w_cur_val, ma?.toString() ?: "—")
        v.setTextViewText(R.id.w_cur_arrow, if (b.charging) "▲" else "▼")

        // ---- RAM ring ----
        v.setImageViewBitmap(R.id.w_ram_ring, Rings.arc(ring, s.ram.fraction, SOFT_WHITE, 0.10f))
        v.setTextViewText(R.id.w_ram_val, String.format(Locale.US, "%.1f", s.ram.usedBytes / GIB))
        v.setTextViewText(R.id.w_ram_sub, String.format(Locale.US, "of %.1fG", s.ram.totalBytes / GIB))

        // ---- CPU ring: a complete ring in one solid colour, mint, amber only at the top clock ----
        val mhz = t.freq.cpuPeakMhz
        val cpuColor = when {
            mhz == null -> GREY
            mhz >= CPU_TOP_MHZ -> AMBER
            else -> MINT
        }
        v.setImageViewBitmap(R.id.w_cpu_ring, Rings.full(ring, cpuColor, 0.10f))
        v.setTextViewText(R.id.w_cpu_ghz, if (mhz == null) "—" else String.format(Locale.US, "%.1f", mhz / 1000f))

        // ---- Top apps: four lines, or one short message when there is no list yet ----
        if (ta.apps.isEmpty()) {
            v.setViewVisibility(R.id.w_apps_note, View.VISIBLE)
            v.setTextViewText(R.id.w_apps_note, if (ta.busy) "Reading…" else noteText(ta.note))
            for (r in appRows) v.setViewVisibility(r.row, View.GONE)
        } else {
            v.setViewVisibility(R.id.w_apps_note, View.GONE)
            for ((i, r) in appRows.withIndex()) {
                val app = ta.apps.getOrNull(i)
                if (app != null) {
                    v.setViewVisibility(r.row, View.VISIBLE)
                    v.setTextViewText(r.rank, "${i + 1}")
                    v.setTextViewText(r.name, shortName(app.label))
                    v.setTextViewText(r.mb, shortMb(app.mb))
                } else {
                    v.setViewVisibility(r.row, View.GONE)
                }
            }
        }

        // ---- Storage bar ----
        val stoPct = (s.storage.fraction * 100).roundToInt()
        v.setTextViewText(
            R.id.w_sto_val,
            "${(s.storage.usedBytes / GB).roundToInt()}/${(s.storage.totalBytes / GB).roundToInt()}",
        )
        v.setProgressBar(R.id.w_sto_bar, 100, stoPct, false)

        // ---- CPU and GPU temperatures ----
        v.setTextViewText(R.id.w_cpu_temp, t.cpuC?.let { "${it.roundToInt()}°" } ?: "—")
        v.setTextViewText(R.id.w_gpu_temp, t.gpuC?.let { "${it.roundToInt()}°" } ?: "—")
        v.setTextColor(R.id.w_cpu_temp, tempColor(t.cpuC))
        v.setTextColor(R.id.w_gpu_temp, tempColor(t.gpuC))
        return v
    }

    /** The widget has little room, so the messages are short. */
    private fun noteText(note: String): String = when (note) {
        "", "Tap Refresh" -> "Tap ↻ to read the top apps"
        "Allow in Shizuku" -> "Allow Phone Status in Shizuku"
        "Shizuku off" -> "Shizuku is off. Start it, then tap ↻"
        else -> note
    }

    /** "Google Play services" -> "Play services"; the text view cuts the rest with "…". */
    private fun shortName(label: String): String {
        val n = label.trim()
        return if (n.length > 10 && n.startsWith("Google ")) n.removePrefix("Google ") else n
    }

    private fun shortMb(mb: Int): String =
        if (mb >= 1024) String.format(Locale.US, "%.1fG", mb / 1024f) else "${mb}M"

    private const val GIB = 1024.0 * 1024.0 * 1024.0
    private const val GB = 1000.0 * 1000.0 * 1000.0

    // ---- colours ---------------------------------------------------------------------------------------------------

    /** Battery: by temperature, and red when nearly empty on battery. */
    private fun batteryColor(b: BatteryInfo): Int = when {
        b.tempC >= BATT_HOT_C || (!b.charging && b.level <= 15) -> RED
        b.tempC >= BATT_WARM_C -> AMBER
        else -> MINT
    }

    /** Current circle: green while drawing little, amber when busy, red when high; amber while charging. */
    private fun currentColor(b: BatteryInfo): Int {
        val ma = b.currentMa ?: return GREY
        return when {
            b.charging -> AMBER
            ma >= DRAIN_HIGH_MA -> RED
            ma >= DRAIN_BUSY_MA -> AMBER
            else -> MINT
        }
    }

    private fun tempColor(c: Float?): Int = when {
        c == null -> GREY
        c >= CHIP_HOT_C -> RED
        c >= CHIP_WARM_C -> AMBER
        else -> MINT
    }

    private fun refreshIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            7,
            Intent(ctx, RefreshReceiver::class.java).setAction(TopApps.ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
