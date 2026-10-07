package dev.ramim.phonestatus.data

/**
 * One-time root tweaks so HyperOS does not kill the live-update service:
 * battery-optimization allowlist, background-run appops, notification permission.
 * Safe to run repeatedly.
 */
object RootSetup {
    fun apply(pkg: String) {
        val script = listOf(
            "dumpsys deviceidle whitelist +$pkg",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
        ).joinToString("\n") { "$it >/dev/null 2>&1" }
        RootShell.exec(script, 20_000)
    }
}
