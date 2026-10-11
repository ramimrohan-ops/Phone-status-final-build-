package dev.ramim.phonestatus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The few building blocks of the plain look: a hairline, a small caption, a label-and-value line and a tappable text. */

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
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextDim) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
        modifier = modifier,
    )
}

/** Grey label on the left, the value on the right. */
@Composable
fun Line(label: String, value: String, valueColor: Color = Palette.TextPrimary) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextLabel, fontSize = 15.sp)
        Text(
            value,
            color = valueColor,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** A tappable accent-coloured text. */
@Composable
fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.Accent) {
    Text(
        text,
        color = color,
        fontSize = 14.sp,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
