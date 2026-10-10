package dev.ramim.phonestatus.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One message from Home Dragon, and what the app decided to do about it. */
data class SwitchEvent(val time: Long, val pkg: String, val decision: String)

/**
 * Shared "should the live updates run right now?" switch.
 *
 * The Home Dragon app watches the screen and sends a small yes/no message: "the home screen is on top, the phone is
 * unlocked, the screen is on, no keyboard and no notification shade". While it says yes it repeats the message every 2 s.
 * Updates run only while a fresh "yes" is in. No message yet, a "no", or no message for 6 s all mean paused.
 * Everything here is also shown on the Debug tab.
 */
object ForegroundGate {

    const val ACTION = "com.ramim.homedragon.HOME_STATE"
    const val HOME_DRAGON_PACKAGE = "com.ramim.homedragon"
    private const val MAX_EVENTS = 40
    private const val STALE_MS = 6_000L

    private val _connected = MutableStateFlow(false)

    /** True once a message from Home Dragon has arrived since the app process started. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _allowed = MutableStateFlow(false)

    /** True only while Home Dragon says the home screen is on top and the message is fresh. */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _why = MutableStateFlow("No message from Home Dragon yet")

    /** The reason Home Dragon (or the app) gave for the current answer. */
    val why: StateFlow<String> = _why.asStateFlow()

    private val _lastSignalAt = MutableStateFlow(0L)
    val lastSignalAt: StateFlow<Long> = _lastSignalAt.asStateFlow()

    private val _events = MutableStateFlow<List<SwitchEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<SwitchEvent>> = _events.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            onSignal(intent.getBooleanExtra("home", false), intent.getStringExtra("why") ?: "")
        }
    }

    /** Starts listening for Home Dragon's messages. Safe to call again; the app context lives as long as the process. */
    @Synchronized
    fun register(ctx: Context) {
        if (registered) return
        try {
            ContextCompat.registerReceiver(
                ctx.applicationContext,
                receiver,
                IntentFilter(ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun onSignal(home: Boolean, reason: String) {
        val text = reason.ifEmpty { if (home) "home on top" else "not home" }
        val changed = !_connected.value || home != _allowed.value || text != _why.value
        _connected.value = true
        _lastSignalAt.value = System.currentTimeMillis()
        if (changed) logEvent(text, if (home) "RUN" else "PAUSED")
        _why.value = text
        _allowed.value = home
    }

    /** Called about once a second: a "yes" with no repeat for 6 s means Home Dragon stopped, so pause. */
    @Synchronized
    fun checkStale() {
        if (_allowed.value && System.currentTimeMillis() - _lastSignalAt.value > STALE_MS) {
            _allowed.value = false
            _why.value = "Signal lost: nothing from Home Dragon for 6 s"
            logEvent("Signal lost (6 s)", "PAUSED")
        }
    }

    @Synchronized
    private fun logEvent(text: String, decision: String) {
        val next = ArrayList<SwitchEvent>(MAX_EVENTS)
        next.add(SwitchEvent(System.currentTimeMillis(), text, decision))
        next.addAll(_events.value.take(MAX_EVENTS - 1))
        _events.value = next
    }

    fun clearLog() {
        _events.value = emptyList()
    }
}

/** What the live widget loop is doing, for the Debug tab. */
object LiveDebug {

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _screenOn = MutableStateFlow(true)
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val _lastUpdateAt = MutableStateFlow(0L)
    val lastUpdateAt: StateFlow<Long> = _lastUpdateAt.asStateFlow()

    private val _rate = MutableStateFlow(0f)

    /** Widget updates per second over the last few updates. */
    val rate: StateFlow<Float> = _rate.asStateFlow()

    private val stamps = ArrayDeque<Long>()

    fun setStatus(value: String) {
        _status.value = value
    }

    fun setScreenOn(value: Boolean) {
        _screenOn.value = value
    }

    @Synchronized
    fun tick() {
        val now = System.currentTimeMillis()
        stamps.addLast(now)
        while (stamps.size > 8) stamps.removeFirst()
        _lastUpdateAt.value = now
        if (stamps.size >= 2) {
            val seconds = (stamps.last() - stamps.first()) / 1000f
            _rate.value = if (seconds > 0f) (stamps.size - 1) / seconds else 0f
        }
    }
}
