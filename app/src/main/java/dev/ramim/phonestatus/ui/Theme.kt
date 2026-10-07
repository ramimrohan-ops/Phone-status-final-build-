package dev.ramim.phonestatus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val BgTop = Color(0xFF111A3A)
    val BgBottom = Color(0xFF05060C)
    val GlassBorder = Color(0x26FFFFFF)

    val TextPrimary = Color(0xFFF2F5FF)
    val TextSecondary = Color(0xFF9AA4C2)

    val Battery = Color(0xFF3DF5A7)
    val BatteryLow = Color(0xFFFF5470)
    val Ram = Color(0xFFA78BFA)
    val Storage = Color(0xFFFFB454)
    val Display = Color(0xFF4CC9F0)

    val TempOk = Color(0xFF3DF5A7)
    val TempWarm = Color(0xFFFFB454)
    val TempHot = Color(0xFFFF5470)
}

@Composable
fun PhoneStatusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Battery,
            background = Palette.BgBottom,
            surface = Palette.BgTop,
            onBackground = Palette.TextPrimary,
            onSurface = Palette.TextPrimary,
        ),
        content = content,
    )
}
