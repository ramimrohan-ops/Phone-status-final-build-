package dev.ramim.phonestatus

import android.app.Application
import dev.ramim.phonestatus.live.ForegroundGate

/**
 * Runs once for every new process of this app, whatever started it (you opening the app, Android restarting it after it was closed,
 * killed or crashed, the widget, a boot, or a message from Home Dragon). It asks Home Dragon once for the state it is in right now.
 */
class PhoneStatusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ForegroundGate.register(this)
    }
}
