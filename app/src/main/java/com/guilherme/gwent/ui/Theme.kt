package com.guilherme.gwent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Gw {
    val Bg = Color(0xFF111214)
    val Surface = Color(0xFF1A1B1F)
    val Surface2 = Color(0xFF24262B)
    val Line = Color(0xFF2E3036)
    val Text = Color(0xFFECECEE)
    val Muted = Color(0xFF8D919A)
    val Accent = Color(0xFFE2B65C)      // dourado das cartas de Gwent
    val OnAccent = Color(0xFF231805)
    val Danger = Color(0xFFE5787A)
    val Ok = Color(0xFF8FD9AE)
}

@Composable
fun GwentTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Gw.Accent,
        onPrimary = Gw.OnAccent,
        secondary = Gw.Accent,
        background = Gw.Bg,
        onBackground = Gw.Text,
        surface = Gw.Surface,
        onSurface = Gw.Text,
        surfaceVariant = Gw.Surface2,
        onSurfaceVariant = Gw.Muted,
        surfaceContainer = Gw.Surface,
        surfaceContainerHigh = Gw.Surface2,
        surfaceContainerHighest = Gw.Surface2,
        outline = Gw.Line,
        error = Gw.Danger,
    )
    val t = Typography(
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = scheme, typography = t, content = content)
}
