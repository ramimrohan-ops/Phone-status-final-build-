package dev.ramim.phonestatus.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ramim.phonestatus.data.TopApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The widget's Refresh button: reads the memory report once and redraws the widget. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopApps.ACTION_REFRESH) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                TopApps.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
