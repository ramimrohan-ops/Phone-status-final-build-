package dev.ramim.phonestatus.live

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One change of state from Home Dragon: [run] is true for "Run", false for "Don't run"; [text] is the reason it gave. */
data class SwitchEvent(val time: Long, val text: String, val run: Boolean)

/**
 * Shared "should the live updates run right now?" switch.
 *
 * Home Dragon watches the screen and sends one of two messages when the answer changes: "Run" (the home screen is on top, on
 * the page where the widget is, the phone is unlocked, the screen is on, no keyboard and no notification shade) or "Don't
 * run" (anything else). Home Dragon decides, this app does not look at page numbers. Every message is answered with
 * "received", and Home Dragon sends it again if that answer does not come. When this app starts it asks Home Dragon for the
 * current state. Updates run while the last answer is "Run"; no answer yet or "Don't run" means paused.
 * Everything here is also shown on the Debug tab.
 */
object ForegroundGate {

    const val ACTION = "com.ramim.homedragon.HOME_STATE"
    const val ACTION_ACK = "dev.ramim.phonestatus.HOME_ACK"
    const val ACTION_ASK = "dev.ramim.phonestatus.HOME_ASK"
    const val HOME_DRAGON_PACKAGE = "com.ramim.homedragon"
    private const val MAX_EVENTS = 20

    private val _connected = MutableStateFlow(false)

    /** True once a message from Home Dragon has arrived since the app process started. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _allowed = MutableStateFlow(false)

    /** True only while Home Dragon's last message is "Run". */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _why = MutableStateFlow("No message from Home Dragon yet")

    /** The reason Home Dragon gave for the current answer. */
    val why: StateFlow<String> = _why.asStateFlow()

    private val _lastSignalAt = MutableStateFlow(0L)
    val lastSignalAt: StateFlow<Long> = _lastSignalAt.asStateFlow()

    private val _events = MutableStateFlow<List<SwitchEvent>>(emptyList())

    /** Changes of state, newest first. */
    val events: StateFlow<List<SwitchEvent>> = _events.asStateFlow()

    private var asked = false

    /**
     * Handles one HOME_STATE message. It arrives through [HomeStateReceiver], which is declared in the manifest, so Home
     * Dragon can reach this app even when it is closed or its process was stopped.
     */
    fun handle(ctx: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val run = intent.getBooleanExtra("run", false)
        val seq = intent.getLongExtra("seq", 0L)
        onSignal(run, intent.getStringExtra("why") ?: "")
        reply(ctx, seq, run)
    }

    /** Tells Home Dragon that message number [seq] arrived and which state it carried. */
    private fun reply(ctx: Context, seq: Long, run: Boolean) {
        try {
            ctx.sendBroadcast(
                Intent(ACTION_ACK)
                    .setPackage(HOME_DRAGON_PACKAGE)
                    .putExtra("seq", seq)
                    .putExtra("run", run),
            )
        } catch (_: Exception) {
        }
    }

    /** Asks Home Dragon for the state it is in right now; it answers with an ordinary message. */
    fun ask(ctx: Context) {
        try {
            ctx.applicationContext.sendBroadcast(Intent(ACTION_ASK).setPackage(HOME_DRAGON_PACKAGE))
        } catch (_: Exception) {
        }
    }

    /** Asks Home Dragon for the state once per process start. Safe to call again. */
    @Synchronized
    fun register(ctx: Context) {
        if (asked) return
        asked = true
        ask(ctx)
    }

    @Synchronized
    private fun onSignal(run: Boolean, reason: String) {
        val text = reason.ifEmpty { if (run) "run" else "don't run" }
        val changed = !_connected.value || run != _allowed.value
        _connected.value = true
        _lastSignalAt.value = System.currentTimeMillis()
        if (changed) {
            val next = ArrayList<SwitchEvent>(MAX_EVENTS)
            next.add(SwitchEvent(System.currentTimeMillis(), text, run))
            next.addAll(_events.value.take(MAX_EVENTS - 1))
            _events.value = next
        }
        _why.value = text
        _allowed.value = run
    }
}

/** What the live widget loop is doing, for the Debug tab. */
object LiveDebug {

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _screenOn = MutableStateFlow(true)
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val _serviceUp = MutableStateFlow(false)

    /** True while the live service is alive. */
    val serviceUp: StateFlow<Boolean> = _serviceUp.asStateFlow()

    fun setServiceUp(value: Boolean) {
        _serviceUp.value = value
    }

    private val _notification = MutableStateFlow(false)

    /** True while the service shows its notification (it is only shown while the widget runs). */
    val notification: StateFlow<Boolean> = _notification.asStateFlow()

    fun setNotification(value: Boolean) {
        _notification.value = value
    }

    private val _lastUpdateAt = MutableStateFlow(0L)
    val lastUpdateAt: StateFlow<Long> = _lastUpdateAt.asStateFlow()

    fun setStatus(value: String) {
        _status.value = value
    }

    fun setScreenOn(value: Boolean) {
        _screenOn.value = value
    }

    fun tick() {
        _lastUpdateAt.value = System.currentTimeMillis()
    }
}
