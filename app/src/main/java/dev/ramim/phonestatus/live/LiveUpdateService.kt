package dev.ramim.phonestatus.live

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.ramim.phonestatus.MainActivity
import dev.ramim.phonestatus.R
import dev.ramim.phonestatus.data.RootSetup
import dev.ramim.phonestatus.data.StatsReader
import dev.ramim.phonestatus.data.ThermalInfo
import dev.ramim.phonestatus.data.ThermalReader
import dev.ramim.phonestatus.widget.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Last thermal reading, so the widget can draw instantly without waiting on root. */
object LiveStats {
    @Volatile var thermal: ThermalInfo? = null
}

/**
 * Pushes the home-screen widget every second while the screen is on.
 * Runs only while Home Dragon says the home screen is on top (and the screen is on). Pauses otherwise, and stops itself when no widget is placed.
 */
class LiveUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val screenOn = MutableStateFlow(true)
    private var loopJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
            LiveDebug.setScreenOn(screenOn.value)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOn.value = pm.isInteractive
        LiveDebug.setScreenOn(screenOn.value)
        ForegroundGate.register(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loopJob == null) loopJob = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        val ctx = applicationContext
        withContext(Dispatchers.IO) { RootSetup.apply(ctx.packageName) }

        while (true) {
            // Suspends here while the screen is off or another app is in front.
            val shouldRun = combine(screenOn, ForegroundGate.allowed) { on, ok -> on && ok }
            if (!shouldRun.first()) {
                LiveDebug.setStatus(
                    if (!screenOn.value) "Paused: screen is off" else "Paused: " + ForegroundGate.why.value,
                )
                shouldRun.first { it }
            }
            LiveDebug.setStatus("Running")
            val t0 = SystemClock.elapsedRealtime()

            if (WidgetRenderer.widgetIds(ctx).isEmpty()) {
                LiveDebug.setStatus("Stopped: no widget on the home screen")
                stopSelf()
                return
            }

            val (stats, thermal) = withContext(Dispatchers.IO) {
                StatsReader.readAll(ctx) to ThermalReader.read()
            }
            LiveStats.thermal = thermal
            WidgetRenderer.renderAll(ctx, stats, thermal)
            LiveDebug.tick()

            val spent = SystemClock.elapsedRealtime() - t0
            delay((1_000L - spent).coerceAtLeast(150L))
        }
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live widget", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the home-screen widget updating every second"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Phone Status")
            .setContentText("Live widget, updating every second while the screen is on")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "live_widget"
        private const val NOTIFICATION_ID = 1

        /** Starts the service; if Android blocks a background start, the app start covers it. */
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LiveUpdateService::class.java))
            } catch (_: Exception) {
            }
        }

        fun startIfWidgets(ctx: Context) {
            if (WidgetRenderer.widgetIds(ctx).isNotEmpty()) start(ctx)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LiveUpdateService::class.java))
        }
    }
}
