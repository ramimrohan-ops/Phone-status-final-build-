package dev.ramim.phonestatus.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the live updates after a reboot if a widget is on the home screen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LiveUpdateService.startIfWidgets(context)
        }
    }
}
