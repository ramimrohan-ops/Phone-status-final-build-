package dev.ramim.phonestatus.data

import android.content.Context
import android.os.SystemClock
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Estimates the battery's real full capacity without root:
 * remaining charge (fuel gauge, µAh) divided by the charge level gives the full capacity.
 *
 * The level is a whole percent, so one reading can be off by about 0.5/level
 * (about 1% at 50%). To cancel that, a weighted average over many readings is kept
 * (higher levels count more) and saved, so it is already settled the next time the app opens.
 */
object CapacityEstimator {
    private const val PREFS = "capacity"
    private const val KEY_AVG = "avg_mah"
    private const val KEY_WEIGHT = "weight"
    private const val SAMPLE_EVERY_MS = 30_000L
    private const val MAX_WEIGHT = 6_000f // about 50 minutes of readings

    private var loaded = false
    private var avg = -1f
    private var weight = 0f
    private var lastSampleAt = 0L

    /** [counterUah] is the remaining charge in µAh, [percent] the rounded battery level. */
    @Synchronized
    fun update(ctx: Context, counterUah: Long, percent: Int): Int? {
        if (!loaded) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            avg = p.getFloat(KEY_AVG, -1f)
            weight = p.getFloat(KEY_WEIGHT, 0f)
            loaded = true
        }

        val instant = instantMah(counterUah, percent)
        val now = SystemClock.elapsedRealtime()
        val due = lastSampleAt == 0L || now - lastSampleAt >= SAMPLE_EVERY_MS
        val usable = instant != null && percent in 15..98 &&
            (avg <= 0f || abs(instant - avg) / avg < 0.15f) // ignore glitchy readings

        if (instant != null && usable && due) {
            lastSampleAt = now
            val w = percent.toFloat()
            if (avg <= 0f) {
                avg = instant
                weight = w
            } else {
                val keep = minOf(weight, MAX_WEIGHT - w)
                avg = (avg * keep + instant * w) / (keep + w)
                weight = keep + w
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_AVG, avg)
                .putFloat(KEY_WEIGHT, weight)
                .apply()
        }

        return when {
            avg > 0f -> avg.roundToInt()
            instant != null -> instant.roundToInt()
            else -> null
        }
    }

    /** One reading. Returns null when the numbers are not believable. */
    internal fun instantMah(counterUah: Long, percent: Int): Float? {
        if (counterUah <= 0L || percent < 5) return null
        val level = percent / 100f
        val fromUah = counterUah / 1000f / level
        if (fromUah in 1500f..20000f) return fromUah
        val fromMah = counterUah / level // a few phones report mAh instead of µAh
        if (fromMah in 1500f..20000f) return fromMah
        return null
    }
}

/** Factory capacity, if the phone exposes it. Cached after the first try. */
object DesignCapacity {
    @Volatile private var cached: Int? = null
    @Volatile private var done = false

    fun read(ctx: Context): Int? {
        if (done) return cached
        val v = sysfsMah("charge_full_design") ?: fromPowerProfile(ctx)
        cached = v
        done = true
        return v
    }

    /** The capacity the phone maker put in the system's power profile (often a rounded typical value). */
    private fun fromPowerProfile(ctx: Context): Int? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val pp = cls.getConstructor(Context::class.java).newInstance(ctx)
        val mah = (cls.getMethod("getBatteryCapacity").invoke(pp) as Double).roundToInt()
        if (mah in 1500..20000) mah else null
    } catch (_: Throwable) {
        null // blocked on newer Android versions
    }
}

/** The battery chip's own measured full capacity. Normally blocked for apps without root. */
object RealCapacity {
    @Volatile private var blocked = false

    fun fullMah(): Int? {
        if (blocked) return null
        val v = sysfsMah("charge_full")
        if (v == null) blocked = true
        return v
    }
}

internal fun sysfsMah(name: String): Int? = try {
    val uah = File("/sys/class/power_supply/battery/$name").readText().trim().toLongOrNull()
    val mah = if (uah == null) null else (uah / 1000L).toInt()
    if (mah != null && mah in 1500..20000) mah else null
} catch (_: Exception) {
    null
}
