package dev.ramim.phonestatus.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One app switch the accessibility service saw, and what the app decided to do about it. */
data class SwitchEvent(val time: Long, val pkg: String, val decision: String)

/**
 * Shared "should the live updates run right now?" switch.
 *
 * The accessibility service (ForegroundWatcherService) reports which app is in front.
 * If that service is not switched on, updates are always allowed (the old behaviour).
 * Everything here is also shown on the Debug tab.
 */
object ForegroundGate {

    private const val MAX_EVENTS = 40

    private val _connected = MutableStateFlow(false)

    /** True while the accessibility service is switched on. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _allowed = MutableStateFlow(true)

    /** True when updates should run: service off, or the home screen / this app is in front. */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _events = MutableStateFlow<List<SwitchEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<SwitchEvent>> = _events.asStateFlow()

    private val _homePackages = MutableStateFlow<Set<String>>(emptySet())
    val homePackages: StateFlow<Set<String>> = _homePackages.asStateFlow()

    private val _keyboardPackages = MutableStateFlow<Set<String>>(emptySet())
    val keyboardPackages: StateFlow<Set<String>> = _keyboardPackages.asStateFlow()

    private var inFront = true

    @Synchronized
    fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) inFront = true
        publish()
    }

    @Synchronized
    fun setInFront(value: Boolean) {
        inFront = value
        publish()
    }

    @Synchronized
    fun logEvent(pkg: String, decision: String) {
        val next = ArrayList<SwitchEvent>(MAX_EVENTS)
        next.add(SwitchEvent(System.currentTimeMillis(), pkg, decision))
        next.addAll(_events.value.take(MAX_EVENTS - 1))
        _events.value = next
    }

    fun clearLog() {
        _events.value = emptyList()
    }

    fun setPackageLists(home: Set<String>, keyboards: Set<String>) {
        if (_homePackages.value != home) _homePackages.value = home
        if (_keyboardPackages.value != keyboards) _keyboardPackages.value = keyboards
    }

    private fun publish() {
        _allowed.value = !_connected.value || inFront
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
