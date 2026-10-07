package dev.ramim.phonestatus.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dev.ramim.phonestatus.data.StatsReader
import dev.ramim.phonestatus.data.ThermalInfo
import dev.ramim.phonestatus.live.LiveStats
import dev.ramim.phonestatus.live.LiveUpdateService

class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) = drawNow(context)

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = drawNow(context)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LiveUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        LiveUpdateService.stop(context)
    }

    /** Quick draw with cached thermal data; the service takes over every second after this. */
    private fun drawNow(context: Context) {
        WidgetRenderer.renderAll(
            context,
            StatsReader.readAll(context),
            LiveStats.thermal ?: ThermalInfo(),
        )
    }
}
