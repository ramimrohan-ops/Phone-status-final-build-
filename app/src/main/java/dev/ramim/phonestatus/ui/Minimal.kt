package dev.ramim.phonestatus.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small building blocks for the minimal look: hairlines, spaced-out labels, thin bars, LED dots. */

@Composable
fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.Hairline),
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextLabel) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
        modifier = modifier,
    )
}

@Composable
fun ThinBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "thin",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color(0x1FFFFFFF)),
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

/** LED dot: a solid core with a soft halo. */
@Composable
fun Led(color: Color, size: Dp = 16.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.5f)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = Palette.TextPrimary,
) {
    Column(modifier) {
        Label(label)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (sub != null) {
            Text(sub, color = Palette.TextLabel, fontSize = 11.sp)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Palette.TextPrimary) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Palette.TextLabel, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
