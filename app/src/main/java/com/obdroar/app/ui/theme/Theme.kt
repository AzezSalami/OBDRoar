package com.obdroar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object RoarColors {
    val Background = Color(0xFF0B0E13)
    val Surface = Color(0xFF141922)
    val SurfaceHigh = Color(0xFF1C2330)
    val Outline = Color(0xFF2A3342)
    val Orange = Color(0xFFFF6A1A)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF2D3D)
    val Green = Color(0xFF2BD67B)
    val Cyan = Color(0xFF2EC5FF)
    val TextDim = Color(0xFF8A94A6)
}

private val scheme = darkColorScheme(
    primary = RoarColors.Orange,
    onPrimary = Color.Black,
    secondary = RoarColors.Cyan,
    tertiary = RoarColors.Amber,
    background = RoarColors.Background,
    onBackground = Color(0xFFE8ECF2),
    surface = RoarColors.Surface,
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = RoarColors.SurfaceHigh,
    onSurfaceVariant = RoarColors.TextDim,
    surfaceContainer = RoarColors.Surface,
    surfaceContainerHigh = RoarColors.SurfaceHigh,
    outline = RoarColors.Outline,
    error = RoarColors.Red,
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun OBDRoarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
