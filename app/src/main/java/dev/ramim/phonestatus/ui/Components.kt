package dev.ramim.phonestatus.ui

import java.util.Locale

fun f1(x: Float): String = String.format(Locale.US, "%.1f", x)

/** 2400 -> "2.40 GHz", 614 -> "614 MHz", null -> "—". [decimals] only applies to GHz. */
fun fmtFreq(mhz: Int?, decimals: Int = 2): String = when {
    mhz == null -> "—"
    mhz >= 1000 -> String.format(Locale.US, "%.${decimals}f GHz", mhz / 1000f)
    else -> "$mhz MHz"
}

/** bytes -> "11.2 GB". base 1024 for RAM, 1000 for storage (what Android Settings uses). */
fun fmtGb(bytes: Long, base: Double = 1024.0): String {
    val gb = bytes / (base * base * base)
    return String.format(Locale.US, if (gb >= 100) "%.0f GB" else "%.1f GB", gb)
}
