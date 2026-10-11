package dev.ramim.phonestatus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** One flat dark background, off-white text, one mint accent and amber / red only for warnings. */
object Palette {
    val Bg = Color(0xFF0B0C10)
    val Hairline = Color(0x1AFFFFFF)
    val TextLabel = Color(0xFF7A8298)
    val TextDim = Color(0xFF585F74)
    val TextPrimary = Color(0xFFEEF1F8)
    val Accent = Color(0xFF3DF5A7)
    val Warn = Color(0xFFFFB454)
    val Hot = Color(0xFFFF5470)
}

@Composable
fun PhoneStatusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            background = Palette.Bg,
            surface = Palette.Bg,
            onBackground = Palette.TextPrimary,
            onSurface = Palette.TextPrimary,
        ),
        content = content,
    )
}
