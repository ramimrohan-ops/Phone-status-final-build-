package dev.ramim.phonestatus.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared "should the live updates run right now?" switch.
 *
 * The accessibility service (ForegroundWatcherService) reports which app is in front.
 * If that service is not switched on, updates are always allowed (the old behaviour).
 */
object ForegroundGate {

    private val _connected = MutableStateFlow(false)

    /** True while the accessibility service is switched on. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _allowed = MutableStateFlow(true)

    /** True when updates should run: service off, or the home screen / this app is in front. */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

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

    private fun publish() {
        _allowed.value = !_connected.value || inFront
    }
}
