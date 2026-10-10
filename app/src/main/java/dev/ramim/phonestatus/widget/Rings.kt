package dev.ramim.phonestatus.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Thin progress rings drawn as bitmaps (a widget cannot draw arcs itself). Same ring is reused while its look is unchanged. */
object Rings {

    private const val TRACK = 0xFF2A2F45.toInt()
    private val cache = HashMap<String, Bitmap>()

    /**
     * A ring [px] pixels wide. [fraction] 0..1 of the circle is drawn in [color], starting at the top, over a dim track.
     * [stroke] is the line width as a share of the size.
     */
    @Synchronized
    fun arc(px: Int, fraction: Float, color: Int, stroke: Float): Bitmap {
        val steps = (fraction.coerceIn(0f, 1f) * 100f).toInt()
        val key = "$px|$steps|$color|$stroke"
        cache[key]?.let { return it }
        if (cache.size > 24) cache.clear()

        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = px * stroke
        val inset = w / 2f + 1f
        val box = RectF(inset, inset, px - inset, px - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = TRACK
        c.drawArc(box, 0f, 360f, false, paint)
        if (steps > 0) {
            paint.color = color
            val sweep = (360f * steps / 100f).coerceAtLeast(6f)
            c.drawArc(box, -90f, sweep, false, paint)
        }
        cache[key] = bmp
        return bmp
    }
}
