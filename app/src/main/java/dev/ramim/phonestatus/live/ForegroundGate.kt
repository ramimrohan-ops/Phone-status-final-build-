package dev.ramim.phonestatus.live

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One message from Home Dragon, and what the app decided to do about it. */
data class SwitchEvent(val time: Long, val pkg: String, val decision: String)

/**
 * Shared "should the live updates run right now?" switch.
 *
 * The Home Dragon app watches the screen and sends a small yes/no message when the answer changes: "the home screen is
 * on top, the phone is unlocked, the screen is on, no keyboard and no notification shade". Every message is answered
 * with "received", and Home Dragon sends it again if that answer does not come. When this app starts it asks Home Dragon
 * for the current state. The message also carries the home-screen page number. Updates run while the last answer is "yes"
 * and the page is [WIDGET_PAGE]; no answer yet, "no", or any other page means paused.
 * Everything here is also shown on the Debug tab.
 */
object ForegroundGate {

    const val ACTION = "com.ramim.homedragon.HOME_STATE"
    const val ACTION_ACK = "dev.ramim.phonestatus.HOME_ACK"
    const val ACTION_ASK = "dev.ramim.phonestatus.HOME_ASK"
    const val HOME_DRAGON_PACKAGE = "com.ramim.homedragon"
    private const val MAX_EVENTS = 40

    /** The home-screen page the widget sits on. The live updates run only while Home Dragon reports this page. */
    const val WIDGET_PAGE = 2

    private val _connected = MutableStateFlow(false)

    /** True once a message from Home Dragon has arrived since the app process started. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _allowed = MutableStateFlow(false)

    /** True only while Home Dragon says the home screen is on top and the message is fresh. */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _why = MutableStateFlow("No message from Home Dragon yet")

    /** The reason Home Dragon (or the app) gave for the current answer. */
    val why: StateFlow<String> = _why.asStateFlow()

    private val _pageText = MutableStateFlow("— (no signal) → paused")

    /** The page line for the Debug tab, for example "2 of 5 → running". */
    val pageText: StateFlow<String> = _pageText.asStateFlow()

    private val _lastSignalAt = MutableStateFlow(0L)
    val lastSignalAt: StateFlow<Long> = _lastSignalAt.asStateFlow()

    private val _events = MutableStateFlow<List<SwitchEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<SwitchEvent>> = _events.asStateFlow()

    private var asked = false

    /**
     * Handles one HOME_STATE message. It arrives through [HomeStateReceiver], which is declared in the manifest, so Home
     * Dragon can reach this app even when it is closed or its process was stopped.
     */
    fun handle(ctx: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val home = intent.getBooleanExtra("home", false)
        val seq = intent.getLongExtra("seq", 0L)
        onSignal(home, intent.getStringExtra("why") ?: "", intent.getIntExtra("page", 0), intent.getIntExtra("pages", 0))
        reply(ctx, seq, home)
    }

    /** Tells Home Dragon that message number [seq] arrived and which state it carried. */
    private fun reply(ctx: Context, seq: Long, home: Boolean) {
        try {
            ctx.sendBroadcast(
                Intent(ACTION_ACK)
                    .setPackage(HOME_DRAGON_PACKAGE)
                    .putExtra("seq", seq)
                    .putExtra("home", home),
            )
            logEvent("received #$seq (${if (home) "home" else "not home"})", "ACK sent")
        } catch (_: Exception) {
        }
    }

    /** Asks Home Dragon for the state it is in right now; it answers with an ordinary message. */
    fun ask(ctx: Context) {
        try {
            ctx.applicationContext.sendBroadcast(Intent(ACTION_ASK).setPackage(HOME_DRAGON_PACKAGE))
            logEvent("asked Home Dragon for the state", "INFO")
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
    private fun onSignal(home: Boolean, reason: String, page: Int, pages: Int) {
        val pageName = if (pages > 0) "$page of $pages" else "$page"
        // The widget runs only on its own page. Home on another page, or a home message without a page number, pauses it.
        val run = home && page == WIDGET_PAGE
        val text = when {
            !home -> reason.ifEmpty { "not home" }
            page <= 0 -> "home, page unknown"
            page != WIDGET_PAGE -> "home, page $pageName (widget is on page $WIDGET_PAGE)"
            else -> "home, page $pageName"
        }
        val line = when {
            !home -> "— (not home) → paused"
            page <= 0 -> "— (page unknown) → paused"
            run -> "$pageName → running"
            else -> "$pageName → paused"
        }
        val changed = !_connected.value || run != _allowed.value || text != _why.value
        _connected.value = true
        _lastSignalAt.value = System.currentTimeMillis()
        if (changed) logEvent(text, if (run) "RUN" else "PAUSED")
        _why.value = text
        _pageText.value = line
        _allowed.value = run
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

    private val _serviceUp = MutableStateFlow(false)

    /** True while the live service is alive. */
    val serviceUp: StateFlow<Boolean> = _serviceUp.asStateFlow()

    fun setServiceUp(value: Boolean) {
        _serviceUp.value = value
    }

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
