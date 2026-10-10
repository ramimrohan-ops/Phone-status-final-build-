package dev.ramim.phonestatus.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives Home Dragon's "home screen is on top / not" message. Declared in the manifest, so the message wakes this app
 * even when it is closed: it is answered with "received" and the live service is started again if it is not running.
 */
class HomeStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ForegroundGate.ACTION) return
        val app = context.applicationContext
        ForegroundGate.handle(app, intent)
        LiveUpdateService.startIfWidgets(app)
    }
}
