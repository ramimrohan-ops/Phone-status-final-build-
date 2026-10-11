package dev.ramim.phonestatus.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives Home Dragon's "Run" / "Don't run" message. Declared in the manifest, so the message wakes this app even when it is
 * closed: it is answered with "received", and on "Run" the live service is started again if it is not running. "Don't run" never
 * starts anything: a running service pauses itself and removes its notification.
 */
class HomeStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ForegroundGate.ACTION) return
        val app = context.applicationContext
        ForegroundGate.handle(app, intent)
        if (ForegroundGate.allowed.value) LiveUpdateService.startIfWidgets(app)
    }
}
