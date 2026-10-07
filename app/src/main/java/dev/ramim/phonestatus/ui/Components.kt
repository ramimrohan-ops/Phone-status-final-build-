package dev.ramim.phonestatus.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun f1(x: Float): String = String.format(Locale.US, "%.1f", x)

/** 4980 -> "4,980 mAh" */
fun fmtMah(mah: Int): String = String.format(Locale.US, "%,d mAh", mah)

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

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Palette.GlassBorder, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, color: Color = Palette.TextSecondary) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
    )
}

@Composable
fun StatLine(label: String, value: String, valueColor: Color = Palette.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Palette.TextSecondary, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2f, s / 2f)

            drawArc(
                color = color.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = s, cap = StrokeCap.Round),
            )
            rotate(degrees = -90f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color)),
                    startAngle = 0f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = s, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun LinearBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "bar",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(shape)
                .background(color),
        )
    }
}

fun tempColor(c: Float?, max: Float): Color {
    if (c == null) return Palette.TextSecondary
    val r = c / max
    return when {
        r < 0.5f -> Palette.TempOk
        r < 0.72f -> Palette.TempWarm
        else -> Palette.TempHot
    }
}

@Composable
fun TempBar(label: String, celsius: Float?, max: Float) {
    val color = tempColor(celsius, max)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Palette.TextSecondary, fontSize = 13.sp)
            Text(
                text = if (celsius == null) "—" else "${f1(celsius)} °C",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearBar(fraction = (celsius ?: 0f) / max, color = color)
    }
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = if (filled) Color(0xFF05060C) else color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
