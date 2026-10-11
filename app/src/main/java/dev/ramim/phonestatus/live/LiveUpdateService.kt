package dev.ramim.phonestatus.live

import android.app.AlarmManager
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
import dev.ramim.phonestatus.data.FreqReader
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Last thermal reading, so the widget can draw instantly without waiting on root. */
object LiveStats {
    @Volatile var thermal: ThermalInfo? = null
}

/**
 * Pushes the home-screen widget every 3 seconds while the screen is on.
 * The CPU and GPU temperatures are read about every 10 seconds and held in between; the CPU clock and everything else every 3 seconds.
 * Runs only while Home Dragon says "Run" (home screen on top, on the widget's page) and the screen is on. It shows its notification
 * only while it runs: on "Don't run" or a dark screen the notification is removed at once and comes back with the next "Run".
 * Stops itself when no widget is placed.
 */
class LiveUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val screenOn = MutableStateFlow(true)
    private var loopJob: Job? = null
    @Volatile private var foreground = false

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
        LiveDebug.setServiceUp(true)
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

    /** Shows the notification (the service is a foreground service while it is shown). False when Android refuses a background start. */
    private fun goForeground(): Boolean {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            foreground = true
            LiveDebug.setNotification(true)
            return true
        } catch (e: Exception) {
            LiveDebug.setStatus("Could not show the notification: " + e.javaClass.simpleName)
            return false
        }
    }

    /** Removes the notification; the service stays alive (as an ordinary service) and waits for the next "Run". */
    private fun leaveForeground() {
        if (!foreground) return
        foreground = false
        LiveDebug.setNotification(false)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification right after every start (the loop removes it again at once when it is not "Run").
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loopJob == null) loopJob = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        val ctx = applicationContext
        withContext(Dispatchers.IO) { RootSetup.apply(ctx.packageName) }

        var lastThermal: ThermalInfo? = null
        var lastThermalAt = 0L

        val shouldRun = combine(screenOn, ForegroundGate.allowed) { on, ok -> on && ok }
        while (true) {
            // Suspends here while the screen is off or Home Dragon says "Don't run"; the notification is gone meanwhile.
            if (!shouldRun.first()) {
                LiveDebug.setStatus(
                    if (!screenOn.value) "Paused: screen is off" else "Paused: " + ForegroundGate.why.value,
                )
                leaveForeground()
                shouldRun.first { it }
            }
            if (!foreground && !goForeground()) {            // "Run" again: the notification comes back
                stopSelf()
                return
            }
            LiveDebug.setStatus("Running")
            val t0 = SystemClock.elapsedRealtime()

            if (WidgetRenderer.widgetIds(ctx).isEmpty()) {
                LiveDebug.setStatus("Stopped: no widget on the home screen")
                stopSelf()
                return
            }

            val (stats, thermal) = withContext(Dispatchers.IO) {
                val now = SystemClock.elapsedRealtime()
                val held = lastThermal
                val t = if (held == null || now - lastThermalAt >= THERMAL_EVERY_MS) {
                    ThermalReader.read().also { lastThermal = it; lastThermalAt = now }
                } else {
                    held.copy(freq = FreqReader.read(held.viaRoot)) // temperatures held, clock speed fresh
                }
                StatsReader.readAll(ctx) to t
            }
            LiveStats.thermal = thermal
            WidgetRenderer.renderAll(ctx, stats, thermal)
            LiveDebug.tick()

            val spent = SystemClock.elapsedRealtime() - t0
            // Wait for the next update, but wake at once if the answer turns to "Don't run" (the notification must go away now).
            withTimeoutOrNull((UPDATE_EVERY_MS - spent).coerceAtLeast(150L)) { shouldRun.first { !it } }
        }
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live widget", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown only while the home-screen widget is updating every 3 seconds"
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
            .setContentText("Live widget, updating every 3 seconds (page 2 only)")
            .addAction(R.drawable.ic_stat, "Open", open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }

    /** Swiped away from recents: ask Android to start the live service again in a moment, in case it was stopped with the app. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val again = PendingIntent.getForegroundService(
                this,
                3,
                Intent(this, LiveUpdateService::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 2_000L, again)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        LiveDebug.setServiceUp(false)
        LiveDebug.setNotification(false)
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

        /** The widget is redrawn this often. */
        private const val UPDATE_EVERY_MS = 3_000L

        /** CPU and GPU temperatures are read again after this long (every third redraw, so about every 10 seconds). */
        private const val THERMAL_EVERY_MS = 9_000L

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
