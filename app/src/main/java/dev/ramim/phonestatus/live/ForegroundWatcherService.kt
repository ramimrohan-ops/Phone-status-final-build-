package dev.ramim.phonestatus.live

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Tells the live widget service which app is in front, so it can pause in other apps.
 * It only listens for "window changed" events and reads the app's package name:
 * no screen contents, no typing, no taps.
 */
class ForegroundWatcherService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundGate.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Notification shade, lock screen, keyboard and the like don't change which app you are in.
        if (pkg == "android" || pkg == "com.android.systemui" || pkg in keyboardPackages()) return

        ForegroundGate.setInFront(pkg == packageName || pkg in homePackages())
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        ForegroundGate.setConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ForegroundGate.setConnected(false)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun homePackages(): Set<String> = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun keyboardPackages(): Set<String> = try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.map { it.packageName }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
